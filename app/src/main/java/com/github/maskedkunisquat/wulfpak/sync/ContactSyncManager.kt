package com.github.maskedkunisquat.wulfpak.sync

import android.content.Context
import android.provider.ContactsContract
import com.github.maskedkunisquat.wulfpak.core.data.AppDatabase
import com.github.maskedkunisquat.wulfpak.core.data.entity.ContactDetail
import com.github.maskedkunisquat.wulfpak.core.data.entity.ContactDetailType
import com.github.maskedkunisquat.wulfpak.core.data.entity.LifeEvent
import com.github.maskedkunisquat.wulfpak.core.data.entity.LifeEventType
import com.github.maskedkunisquat.wulfpak.core.data.entity.Person
import com.github.maskedkunisquat.wulfpak.core.data.entity.RelationLabel
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

class ContactSyncManager(private val db: AppDatabase) {

    data class SyncResult(val added: Int, val skipped: Int)

    data class ContactCandidate(
        val contactId: String,
        val displayName: String,
        val firstName: String,
        val lastName: String?,
        val alreadyImported: Boolean,
    )

    suspend fun fetchCandidates(context: Context): List<ContactCandidate> {
        val existing = db.personDao().getAllOnce()
        val existingKeys = existing.map { normalizeKey(it.firstName, it.lastName) }.toSet()

        val result = mutableListOf<ContactCandidate>()
        val cursor = context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
            null, null,
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC",
        ) ?: return emptyList()

        cursor.use { c ->
            val idCol   = c.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
            val nameCol = c.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)

            while (c.moveToNext()) {
                val contactId   = c.getString(idCol) ?: continue
                val displayName = c.getString(nameCol)?.trim() ?: continue
                if (displayName.isBlank()) continue

                val (firstName, lastName) = splitDisplayName(displayName)
                result += ContactCandidate(
                    contactId       = contactId,
                    displayName     = displayName,
                    firstName       = firstName,
                    lastName        = lastName,
                    alreadyImported = normalizeKey(firstName, lastName) in existingKeys,
                )
            }
        }

        return result
    }

    suspend fun importWithRelations(
        context: Context,
        assignments: List<Pair<ContactCandidate, String>>,
    ): SyncResult {
        val existing = db.personDao().getAllOnce()
        val existingKeys = existing.map { normalizeKey(it.firstName, it.lastName) }.toMutableSet()
        val cr = context.contentResolver
        var added = 0; var skipped = 0

        for ((candidate, relation) in assignments) {
            val key = normalizeKey(candidate.firstName, candidate.lastName)
            if (key in existingKeys) { skipped++; continue }

            // ── Nickname & org ────────────────────────────────────────────
            val nickname = queryNickname(context, candidate.contactId)
            val (company, jobTitle) = queryOrganization(context, candidate.contactId)

            // ── Photo ─────────────────────────────────────────────────────
            val photoPath = copyPhoto(context, candidate.contactId)

            val person = Person(
                firstName     = candidate.firstName,
                lastName      = candidate.lastName,
                nickname      = nickname,
                photoUri      = photoPath,
                relationLabel = relation,
                company       = company,
                jobTitle      = jobTitle,
            )
            db.personDao().insert(person)
            existingKeys.add(key)

            // ── Phone numbers (deduped) ───────────────────────────────────
            val details = mutableListOf<ContactDetail>()
            val seenPhones = mutableSetOf<String>()

            cr.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER,
                    ContactsContract.CommonDataKinds.Phone.TYPE,
                ),
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                arrayOf(candidate.contactId), null,
            )?.use { pc ->
                val numCol  = pc.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val normCol = pc.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER)
                val typeCol = pc.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE)
                while (pc.moveToNext()) {
                    val raw    = pc.getString(numCol)?.trim() ?: continue
                    val norm   = pc.getString(normCol)?.trim()
                    val dedupKey = norm?.takeIf { it.isNotBlank() } ?: raw.filter { it.isDigit() }
                    if (dedupKey.isBlank() || !seenPhones.add(dedupKey)) continue
                    val label = ContactsContract.CommonDataKinds.Phone
                        .getTypeLabel(context.resources, pc.getInt(typeCol), "Mobile").toString()
                    details += ContactDetail(
                        personId = person.id,
                        type     = ContactDetailType.PHONE,
                        label    = label,
                        value    = raw,
                    )
                }
            }

            // ── Email addresses (deduped) ─────────────────────────────────
            val seenEmails = mutableSetOf<String>()

            cr.query(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Email.ADDRESS,
                    ContactsContract.CommonDataKinds.Email.TYPE,
                ),
                "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?",
                arrayOf(candidate.contactId), null,
            )?.use { ec ->
                val addrCol = ec.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.ADDRESS)
                val typeCol = ec.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.TYPE)
                while (ec.moveToNext()) {
                    val address  = ec.getString(addrCol)?.trim() ?: continue
                    val dedupKey = address.lowercase()
                    if (dedupKey.isBlank() || !seenEmails.add(dedupKey)) continue
                    val label = ContactsContract.CommonDataKinds.Email
                        .getTypeLabel(context.resources, ec.getInt(typeCol), "Email").toString()
                    details += ContactDetail(
                        personId = person.id,
                        type     = ContactDetailType.EMAIL,
                        label    = label,
                        value    = address,
                    )
                }
            }

            if (details.isNotEmpty()) db.contactDetailDao().insertAll(details)

            // ── Life events (birthday, anniversary, fuzzy others) ─────────
            val lifeEvents = queryLifeEvents(context, candidate.contactId, person.id)
            lifeEvents.forEach { db.lifeEventDao().insert(it) }

            added++
        }

        return SyncResult(added, skipped)
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun queryNickname(context: Context, contactId: String): String? =
        context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Nickname.NAME),
            "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(contactId, ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE),
            null,
        )?.use { c ->
            if (c.moveToFirst())
                c.getString(c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Nickname.NAME))
                    ?.trim()?.takeIf { it.isNotBlank() }
            else null
        }

    private fun queryOrganization(context: Context, contactId: String): Pair<String?, String?> =
        context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Organization.COMPANY,
                ContactsContract.CommonDataKinds.Organization.TITLE,
            ),
            "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(contactId, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE),
            null,
        )?.use { c ->
            if (c.moveToFirst()) {
                val company  = c.getString(c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Organization.COMPANY))
                    ?.trim()?.takeIf { it.isNotBlank() }
                val jobTitle = c.getString(c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Organization.TITLE))
                    ?.trim()?.takeIf { it.isNotBlank() }
                company to jobTitle
            } else null to null
        } ?: (null to null)

    private fun copyPhoto(context: Context, contactId: String): String? {
        return try {
            val contactUri = android.net.Uri.withAppendedPath(
                ContactsContract.Contacts.CONTENT_URI,
                contactId,
            )
            val photoUri = ContactsContract.Contacts.openContactPhotoInputStream(
                context.contentResolver, contactUri, false, // false = thumbnail
            ) ?: return null

            val photosDir = File(context.filesDir, "photos").also { it.mkdirs() }
            val file = File(photosDir, "${UUID.randomUUID()}.jpg")
            photoUri.use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            }
            file.absolutePath
        } catch (_: Exception) { null }
    }

    private fun queryLifeEvents(context: Context, contactId: String, personId: UUID): List<LifeEvent> {
        val events = mutableListOf<LifeEvent>()
        context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Event.START_DATE,
                ContactsContract.CommonDataKinds.Event.TYPE,
                ContactsContract.CommonDataKinds.Event.LABEL,
            ),
            "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(contactId, ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE),
            null,
        )?.use { c ->
            val dateCol  = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Event.START_DATE)
            val typeCol  = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Event.TYPE)
            val labelCol = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Event.LABEL)
            while (c.moveToNext()) {
                val dateStr = c.getString(dateCol)?.trim() ?: continue
                val type    = c.getInt(typeCol)
                val label   = c.getString(labelCol)?.trim() ?: ""

                val eventType = when (type) {
                    ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY    -> LifeEventType.BIRTHDAY
                    ContactsContract.CommonDataKinds.Event.TYPE_ANNIVERSARY -> LifeEventType.ANNIVERSARY
                    else -> fuzzyMatchEventType(label) ?: continue
                }

                val (epochMs, isRecurring) = parseDateString(dateStr, eventType) ?: continue
                events += LifeEvent(
                    personId    = personId,
                    eventType   = eventType,
                    date        = epochMs,
                    isRecurring = isRecurring,
                )
            }
        }
        return events
    }

    private fun fuzzyMatchEventType(label: String): String? {
        val l = label.lowercase()
        return when {
            "birthday" in l                               -> LifeEventType.BIRTHDAY
            "anniversar" in l                             -> LifeEventType.ANNIVERSARY
            "graduat" in l                                -> LifeEventType.GRADUATION
            "job" in l || "hire" in l || "start" in l    -> LifeEventType.JOB_CHANGE
            "mov" in l || "relocat" in l                  -> LifeEventType.MOVED
            else                                          -> null
        }
    }

    // Returns epoch ms + isRecurring. Year 1900 sentinel = no year in source data.
    private fun parseDateString(dateStr: String, eventType: String): Pair<Long, Boolean>? {
        val recurring = eventType in listOf(LifeEventType.BIRTHDAY, LifeEventType.ANNIVERSARY)
        return try {
            if (dateStr.startsWith("--")) {
                // "--MM-DD" format: no year known — use 1900 as sentinel
                val mmdd = dateStr.removePrefix("--")
                val cal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, 1900)
                    set(Calendar.MONTH, mmdd.substring(0, 2).toInt() - 1)
                    set(Calendar.DAY_OF_MONTH, mmdd.substring(3, 5).toInt())
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
                cal.timeInMillis to recurring
            } else {
                val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
                fmt.parse(dateStr)?.time?.let { it to recurring }
            }
        } catch (_: Exception) { null }
    }

    private fun splitDisplayName(displayName: String): Pair<String, String?> {
        val parts = displayName.trim().split(" ", limit = 2)
        return parts[0] to parts.getOrNull(1)?.trim()?.ifBlank { null }
    }

    private fun normalizeKey(first: String, last: String?): String =
        "${first.trim().lowercase()}|${last?.trim()?.lowercase() ?: ""}"
}
