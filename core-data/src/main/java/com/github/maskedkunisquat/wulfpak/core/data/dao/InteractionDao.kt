package com.github.maskedkunisquat.wulfpak.core.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.github.maskedkunisquat.wulfpak.core.data.entity.Interaction
import com.github.maskedkunisquat.wulfpak.core.data.entity.InteractionParticipant
import com.github.maskedkunisquat.wulfpak.core.data.entity.Person
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface InteractionDao {

    @Query("SELECT * FROM interactions ORDER BY timestamp DESC")
    fun getAll(): Flow<List<Interaction>>

    @Query("""
        SELECT i.* FROM interactions i
        INNER JOIN interaction_participants ip ON i.id = ip.interactionId
        WHERE ip.personId = :personId
        ORDER BY i.timestamp DESC
    """)
    fun getForPerson(personId: UUID): Flow<List<Interaction>>

    @Query("SELECT * FROM interactions WHERE id = :id")
    suspend fun getById(id: UUID): Interaction?

    @Query("SELECT * FROM interactions WHERE id = :id")
    fun observe(id: UUID): Flow<Interaction?>

    @Query("""
        SELECT p.* FROM persons p
        INNER JOIN interaction_participants ip ON p.id = ip.personId
        WHERE ip.interactionId = :interactionId
        ORDER BY p.firstName
    """)
    fun getParticipants(interactionId: UUID): Flow<List<Person>>

    @Query("SELECT * FROM interactions WHERE embedding IS NULL")
    suspend fun getUnembedded(): List<Interaction>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(interaction: Interaction)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertParticipant(participant: InteractionParticipant)

    @Update
    suspend fun update(interaction: Interaction)

    @Delete
    suspend fun delete(interaction: Interaction)

    @Delete
    suspend fun deleteParticipant(participant: InteractionParticipant)

    @Query("SELECT personId FROM interaction_participants WHERE interactionId = :interactionId")
    suspend fun getParticipantIds(interactionId: UUID): List<UUID>

    @Query("SELECT * FROM interaction_participants WHERE personId = :personId")
    suspend fun getParticipantsByPerson(personId: UUID): List<InteractionParticipant>

    @Query("UPDATE interactions SET embedding = :embedding WHERE id = :id")
    suspend fun updateEmbedding(id: UUID, embedding: FloatArray)
}
