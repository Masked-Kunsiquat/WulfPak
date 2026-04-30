package com.github.maskedkunisquat.wulfpak.core.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.github.maskedkunisquat.wulfpak.core.data.entity.Person
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface PersonDao {

    @Query("SELECT * FROM persons ORDER BY firstName, lastName")
    fun getAll(): Flow<List<Person>>

    @Query("SELECT * FROM persons ORDER BY firstName, lastName")
    suspend fun getAllOnce(): List<Person>

    @Query("SELECT * FROM persons WHERE isFavorite = 1 ORDER BY firstName, lastName")
    fun getFavorites(): Flow<List<Person>>

    @Query("SELECT * FROM persons WHERE id = :id")
    suspend fun getById(id: UUID): Person?

    @Query("SELECT * FROM persons WHERE id = :id")
    fun observe(id: UUID): Flow<Person?>

    @Query("""
        SELECT * FROM persons
        WHERE firstName LIKE '%' || :query || '%'
           OR lastName  LIKE '%' || :query || '%'
           OR nickname  LIKE '%' || :query || '%'
        ORDER BY firstName, lastName
    """)
    fun search(query: String): Flow<List<Person>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(person: Person)

    @Update
    suspend fun update(person: Person)

    @Delete
    suspend fun delete(person: Person)

    @Query("""
        UPDATE persons
        SET lastContactedAt = :timestamp, interactionCount = interactionCount + 1
        WHERE id = :personId
    """)
    suspend fun onInteractionAdded(personId: UUID, timestamp: Long)

    @Query("UPDATE persons SET interactionCount = MAX(0, interactionCount - 1) WHERE id = :personId")
    suspend fun onInteractionDeleted(personId: UUID)

    @Query("UPDATE persons SET cachedSummary = :summary, summaryGeneratedAt = :generatedAt WHERE id = :id")
    suspend fun updateSummary(id: UUID, summary: String, generatedAt: Long)
}
