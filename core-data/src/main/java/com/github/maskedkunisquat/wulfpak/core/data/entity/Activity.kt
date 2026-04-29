package com.github.maskedkunisquat.wulfpak.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "activities")
data class Activity(
    @PrimaryKey val id: UUID = UUID.randomUUID(),
    val timestamp: Long,
    val title: String,
    val body: String? = null,
    val embedding: FloatArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Activity) return false
        return id == other.id &&
            timestamp == other.timestamp &&
            title == other.title &&
            body == other.body &&
            embedding.contentEquals(other.embedding)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + (body?.hashCode() ?: 0)
        result = 31 * result + (embedding?.contentHashCode() ?: 0)
        return result
    }
}
