package com.github.maskedkunisquat.wulfpak.core.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

object GiftStatus {
    const val IDEA = "IDEA"
    const val PURCHASED = "PURCHASED"
    const val GIVEN = "GIVEN"
}

@Entity(
    tableName = "gifts",
    foreignKeys = [ForeignKey(
        entity = Person::class,
        parentColumns = ["id"],
        childColumns = ["personId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("personId")]
)
data class Gift(
    @PrimaryKey val id: UUID = UUID.randomUUID(),
    val personId: UUID,
    val name: String,
    val occasion: String? = null,
    val status: String = GiftStatus.IDEA,
    val note: String? = null
)
