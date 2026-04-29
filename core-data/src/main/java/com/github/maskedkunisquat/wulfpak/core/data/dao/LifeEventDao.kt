package com.github.maskedkunisquat.wulfpak.core.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.github.maskedkunisquat.wulfpak.core.data.entity.LifeEvent
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface LifeEventDao {

    @Query("SELECT * FROM life_events WHERE personId = :personId ORDER BY date")
    fun getForPerson(personId: UUID): Flow<List<LifeEvent>>

    @Query("SELECT * FROM life_events WHERE isRecurring = 1 ORDER BY date")
    fun getAllRecurring(): Flow<List<LifeEvent>>

    @Query("SELECT * FROM life_events WHERE id = :id")
    suspend fun getById(id: UUID): LifeEvent?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(lifeEvent: LifeEvent)

    @Update
    suspend fun update(lifeEvent: LifeEvent)

    @Delete
    suspend fun delete(lifeEvent: LifeEvent)
}
