package com.github.maskedkunisquat.wulfpak.core.data.entity

import androidx.room.ColumnInfo
import java.util.UUID

data class EmbeddingRow(
    @ColumnInfo(name = "id") val id: UUID,
    @ColumnInfo(name = "embedding") val embedding: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EmbeddingRow) return false
        return id == other.id && embedding.contentEquals(other.embedding)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}
