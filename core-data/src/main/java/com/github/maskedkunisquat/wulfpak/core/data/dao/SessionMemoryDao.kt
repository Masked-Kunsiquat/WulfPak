package com.github.maskedkunisquat.wulfpak.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.maskedkunisquat.wulfpak.core.data.entity.SessionMemory

@Dao
interface SessionMemoryDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(memory: SessionMemory)

    @Query("SELECT * FROM session_memories ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<SessionMemory>
}
