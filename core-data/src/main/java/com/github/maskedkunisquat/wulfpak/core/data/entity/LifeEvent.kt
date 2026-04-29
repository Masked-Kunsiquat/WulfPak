package com.github.maskedkunisquat.wulfpak.core.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

object LifeEventType {
    const val BIRTHDAY = "birthday"
    const val ANNIVERSARY = "anniversary"
    const val DEATH = "death"
    const val GRADUATION = "graduation"
    const val JOB_CHANGE = "job_change"
    const val MOVED = "moved"
}

@Entity(
    tableName = "life_events",
    foreignKeys = [ForeignKey(
        entity = Person::class,
        parentColumns = ["id"],
        childColumns = ["personId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("personId")]
)
data class LifeEvent(
    @PrimaryKey val id: UUID = UUID.randomUUID(),
    val personId: UUID,
    val eventType: String,
    val date: Long,
    val isRecurring: Boolean = false,
    val note: String? = null
)
