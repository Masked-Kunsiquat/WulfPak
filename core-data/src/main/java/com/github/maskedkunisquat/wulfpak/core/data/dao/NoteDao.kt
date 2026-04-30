package com.github.maskedkunisquat.wulfpak.core.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.github.maskedkunisquat.wulfpak.core.data.entity.Note
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface NoteDao {

    @Query("SELECT * FROM notes ORDER BY timestamp DESC")
    fun getAll(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE personId = :personId ORDER BY timestamp DESC")
    fun getForPerson(personId: UUID): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE personId IS NULL ORDER BY timestamp DESC")
    fun getStandalone(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: UUID): Note?

    @Query("SELECT * FROM notes WHERE embedding IS NULL")
    suspend fun getUnembedded(): List<Note>

    @Query("UPDATE notes SET personId = :toId WHERE personId = :fromId")
    suspend fun reassignToPerson(fromId: UUID, toId: UUID)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(note: Note)

    @Update
    suspend fun update(note: Note)

    @Delete
    suspend fun delete(note: Note)

    @Query("UPDATE notes SET embedding = :embedding WHERE id = :id")
    suspend fun updateEmbedding(id: UUID, embedding: ByteArray)
}
