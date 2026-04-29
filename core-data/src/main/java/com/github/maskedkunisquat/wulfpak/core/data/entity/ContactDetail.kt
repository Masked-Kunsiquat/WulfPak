package com.github.maskedkunisquat.wulfpak.core.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

object ContactDetailType {
    const val PHONE = "PHONE"
    const val EMAIL = "EMAIL"
    const val SOCIAL = "SOCIAL"
    const val ADDRESS = "ADDRESS"
}

@Entity(
    tableName = "contact_details",
    foreignKeys = [ForeignKey(
        entity = Person::class,
        parentColumns = ["id"],
        childColumns = ["personId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("personId")]
)
data class ContactDetail(
    @PrimaryKey val id: UUID = UUID.randomUUID(),
    val personId: UUID,
    val type: String,
    val label: String,
    val value: String
)
