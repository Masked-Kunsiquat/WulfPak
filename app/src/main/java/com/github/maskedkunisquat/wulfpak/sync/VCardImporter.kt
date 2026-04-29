package com.github.maskedkunisquat.wulfpak.sync

import com.github.maskedkunisquat.wulfpak.core.data.AppDatabase
import com.github.maskedkunisquat.wulfpak.core.data.entity.ContactDetail
import com.github.maskedkunisquat.wulfpak.core.data.entity.ContactDetailType
import com.github.maskedkunisquat.wulfpak.core.data.entity.Person
import com.github.maskedkunisquat.wulfpak.core.data.entity.RelationLabel
import ezvcard.Ezvcard
import java.io.InputStream

class VCardImporter(private val db: AppDatabase) {

    data class ImportResult(val added: Int)

    suspend fun import(inputStream: InputStream): ImportResult {
        val vcards = Ezvcard.parse(inputStream).all()
        val existing = db.personDao().getAllOnce()
        val existingKeys = existing.map { normalizeKey(it.firstName, it.lastName) }.toMutableSet()

        var added = 0

        for (vcard in vcards) {
            val sn = vcard.structuredName
            val fn = vcard.formattedName?.value?.trim()

            val firstName = sn?.given?.trim()?.ifBlank { null }
                ?: fn?.split(" ")?.firstOrNull()?.trim()?.ifBlank { null }
                ?: continue

            val lastName = sn?.family?.trim()?.ifBlank { null }
                ?: fn?.split(" ", limit = 2)?.getOrNull(1)?.trim()?.ifBlank { null }

            val key = normalizeKey(firstName, lastName)
            if (key in existingKeys) continue

            val person = Person(
                firstName     = firstName,
                lastName      = lastName,
                relationLabel = RelationLabel.ACQUAINTANCE,
            )
            db.personDao().insert(person)
            existingKeys.add(key)

            val details = mutableListOf<ContactDetail>()

            for (phone in vcard.telephoneNumbers) {
                val number = phone.text?.trim() ?: continue
                val label  = phone.types.firstOrNull()?.value ?: "Mobile"
                details += ContactDetail(
                    personId = person.id,
                    type     = ContactDetailType.PHONE,
                    label    = label,
                    value    = number,
                )
            }

            for (email in vcard.emails) {
                val address = email.value?.trim() ?: continue
                val label   = email.types.firstOrNull()?.value ?: "Email"
                details += ContactDetail(
                    personId = person.id,
                    type     = ContactDetailType.EMAIL,
                    label    = label,
                    value    = address,
                )
            }

            if (details.isNotEmpty()) db.contactDetailDao().insertAll(details)
            added++
        }

        return ImportResult(added)
    }

    private fun normalizeKey(first: String, last: String?): String =
        "${first.trim().lowercase()}|${last?.trim()?.lowercase() ?: ""}"
}
