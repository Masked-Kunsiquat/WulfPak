package com.github.maskedkunisquat.wulfpak.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "session_memories", indices = [Index("timestamp")])
data class SessionMemory(
    @PrimaryKey val id: UUID = UUID.randomUUID(),
    val timestamp: Long,
    val summary: String,
)
