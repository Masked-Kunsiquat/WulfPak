package com.github.maskedkunisquat.wulfpak.sync

import android.content.Context
import android.provider.ContactsContract
import com.github.maskedkunisquat.wulfpak.core.data.AppDatabase
import com.github.maskedkunisquat.wulfpak.core.data.entity.ContactDetail
import com.github.maskedkunisquat.wulfpak.core.data.entity.ContactDetailType
import com.github.maskedkunisquat.wulfpak.core.data.entity.Person
import com.github.maskedkunisquat.wulfpak.core.data.entity.RelationLabel

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

    suspend fun importSelected(context: Context, selected: List<ContactCandidate>): SyncResult {
        val existing = db.personDao().getAllOnce()
        val existingKeys = existing.map { normalizeKey(it.firstName, it.lastName) }.toMutableSet()
        val cr = context.contentResolver

        var added = 0
        var skipped = 0

        for (candidate in selected) {
            val key = normalizeKey(candidate.firstName, candidate.lastName)
            if (key in existingKeys) { skipped++; continue }

            val person = Person(
                firstName     = candidate.firstName,
                lastName      = candidate.lastName,
                relationLabel = RelationLabel.ACQUAINTANCE,
            )
            db.personDao().insert(person)
            existingKeys.add(key)

            val details = mutableListOf<ContactDetail>()

            cr.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.TYPE,
                ),
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                arrayOf(candidate.contactId), null,
            )?.use { pc ->
                val numCol  = pc.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val typeCol = pc.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE)
                while (pc.moveToNext()) {
                    val number = pc.getString(numCol)?.trim() ?: continue
                    val label  = ContactsContract.CommonDataKinds.Phone
                        .getTypeLabel(context.resources, pc.getInt(typeCol), "Mobile")
                        .toString()
                    details += ContactDetail(
                        personId = person.id,
                        type     = ContactDetailType.PHONE,
                        label    = label,
                        value    = number,
                    )
                }
            }

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
                    val address = ec.getString(addrCol)?.trim() ?: continue
                    val label   = ContactsContract.CommonDataKinds.Email
                        .getTypeLabel(context.resources, ec.getInt(typeCol), "Email")
                        .toString()
                    details += ContactDetail(
                        personId = person.id,
                        type     = ContactDetailType.EMAIL,
                        label    = label,
                        value    = address,
                    )
                }
            }

            if (details.isNotEmpty()) db.contactDetailDao().insertAll(details)
            added++
        }

        return SyncResult(added, skipped)
    }

    private fun splitDisplayName(displayName: String): Pair<String, String?> {
        val parts = displayName.trim().split(" ", limit = 2)
        return parts[0] to parts.getOrNull(1)?.trim()?.ifBlank { null }
    }

    private fun normalizeKey(first: String, last: String?): String =
        "${first.trim().lowercase()}|${last?.trim()?.lowercase() ?: ""}"
}
