package com.github.maskedkunisquat.wulfpak.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "session_memories")
data class SessionMemory(
    @PrimaryKey val id: UUID = UUID.randomUUID(),
    val timestamp: Long,
    val summary: String,
)
