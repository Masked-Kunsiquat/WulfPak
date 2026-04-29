package com.github.maskedkunisquat.wulfpak.core.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import java.util.UUID

@Entity(
    tableName = "interaction_participants",
    primaryKeys = ["interactionId", "personId"],
    foreignKeys = [
        ForeignKey(
            entity = Interaction::class,
            parentColumns = ["id"],
            childColumns = ["interactionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Person::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("personId")]
)
data class InteractionParticipant(
    val interactionId: UUID,
    val personId: UUID
)
