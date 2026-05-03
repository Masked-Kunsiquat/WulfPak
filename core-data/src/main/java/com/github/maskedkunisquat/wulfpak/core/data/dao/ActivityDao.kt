package com.github.maskedkunisquat.wulfpak.core.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.github.maskedkunisquat.wulfpak.core.data.entity.Activity
import com.github.maskedkunisquat.wulfpak.core.data.entity.ActivityParticipant
import com.github.maskedkunisquat.wulfpak.core.data.entity.EmbeddingRow
import com.github.maskedkunisquat.wulfpak.core.data.entity.Person
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface ActivityDao {

    @Query("SELECT * FROM activities ORDER BY timestamp DESC")
    fun getAll(): Flow<List<Activity>>

    @Query("""
        SELECT a.* FROM activities a
        INNER JOIN activity_participants ap ON a.id = ap.activityId
        WHERE ap.personId = :personId
        ORDER BY a.timestamp DESC
    """)
    fun getForPerson(personId: UUID): Flow<List<Activity>>

    @Query("SELECT * FROM activities WHERE id = :id")
    suspend fun getById(id: UUID): Activity?

    @Query("SELECT * FROM activities WHERE id = :id")
    fun observe(id: UUID): Flow<Activity?>

    @Query("""
        SELECT p.* FROM persons p
        INNER JOIN activity_participants ap ON p.id = ap.personId
        WHERE ap.activityId = :activityId
        ORDER BY p.firstName
    """)
    fun getParticipants(activityId: UUID): Flow<List<Person>>

    @Query("SELECT * FROM activities ORDER BY timestamp DESC")
    suspend fun getAllOnce(): List<Activity>

    @Query("SELECT * FROM activity_participants")
    suspend fun getAllParticipants(): List<ActivityParticipant>

    @Query("SELECT * FROM activities WHERE embedding IS NULL")
    suspend fun getUnembedded(): List<Activity>

    @Query("SELECT id, embedding FROM activities WHERE embedding IS NOT NULL")
    suspend fun getEmbedded(): List<EmbeddingRow>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(activity: Activity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertParticipant(participant: ActivityParticipant)

    @Update
    suspend fun update(activity: Activity)

    @Delete
    suspend fun delete(activity: Activity)

    @Delete
    suspend fun deleteParticipant(participant: ActivityParticipant)

    @Query("SELECT personId FROM activity_participants WHERE activityId = :activityId")
    suspend fun getParticipantIds(activityId: UUID): List<UUID>

    @Query("""
        SELECT a.timestamp FROM activities a
        INNER JOIN activity_participants ap ON a.id = ap.activityId
        WHERE ap.personId = :personId
    """)
    suspend fun getTimestampsForPerson(personId: UUID): List<Long>

    @Query("SELECT * FROM activity_participants WHERE activityId IN (:ids)")
    suspend fun getParticipantsForIds(ids: List<UUID>): List<ActivityParticipant>

    @Query("SELECT * FROM activity_participants WHERE personId = :personId")
    suspend fun getParticipantsByPerson(personId: UUID): List<ActivityParticipant>

    @Query("UPDATE activities SET embedding = :embedding WHERE id = :id")
    suspend fun updateEmbedding(id: UUID, embedding: ByteArray)
}
