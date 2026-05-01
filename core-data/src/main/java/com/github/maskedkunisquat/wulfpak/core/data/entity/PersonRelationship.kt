package com.github.maskedkunisquat.wulfpak.core.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import java.util.UUID

@Entity(
    tableName = "person_relationships",
    primaryKeys = ["personAId", "personBId"],
    foreignKeys = [
        ForeignKey(
            entity = Person::class,
            parentColumns = ["id"],
            childColumns = ["personAId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Person::class,
            parentColumns = ["id"],
            childColumns = ["personBId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("personBId")]
)
data class PersonRelationship(
    val personAId: UUID,
    val personBId: UUID,
    val label: String,
    val category: String = RelCategory.OTHER.name,
    val relType: String? = null,
)
