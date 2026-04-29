package com.github.maskedkunisquat.wulfpak.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

object InteractionType {
    const val CALL = "call"
    const val TEXT = "text"
    const val EMAIL = "email"
    const val VIDEO_CALL = "video_call"
    const val IN_PERSON = "in_person"
    const val SOCIAL_MEDIA = "social_media"
}

@Entity(tableName = "interactions")
data class Interaction(
    @PrimaryKey val id: UUID = UUID.randomUUID(),
    val timestamp: Long,
    val type: String,
    val durationSeconds: Int? = null,
    val note: String? = null,
    val embedding: FloatArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Interaction) return false
        return id == other.id &&
            timestamp == other.timestamp &&
            type == other.type &&
            durationSeconds == other.durationSeconds &&
            note == other.note &&
            embedding.contentEquals(other.embedding)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + (durationSeconds ?: 0)
        result = 31 * result + (note?.hashCode() ?: 0)
        result = 31 * result + (embedding?.contentHashCode() ?: 0)
        return result
    }
}
