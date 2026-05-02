package com.github.maskedkunisquat.wulfpak.core.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.github.maskedkunisquat.wulfpak.core.data.entity.Gift
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface GiftDao {

    @Query("SELECT * FROM gifts WHERE personId = :personId")
    fun getForPerson(personId: UUID): Flow<List<Gift>>

    @Query("SELECT * FROM gifts WHERE status = :status")
    fun getByStatus(status: String): Flow<List<Gift>>

    @Query("SELECT * FROM gifts")
    suspend fun getAllOnce(): List<Gift>

    @Query("SELECT * FROM gifts WHERE id = :id")
    suspend fun getById(id: UUID): Gift?

    @Query("UPDATE gifts SET personId = :toId WHERE personId = :fromId")
    suspend fun reassignToPerson(fromId: UUID, toId: UUID)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(gift: Gift)

    @Update
    suspend fun update(gift: Gift)

    @Delete
    suspend fun delete(gift: Gift)
}
