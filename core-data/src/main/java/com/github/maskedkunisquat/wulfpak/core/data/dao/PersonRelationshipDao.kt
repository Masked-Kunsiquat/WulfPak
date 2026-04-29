package com.github.maskedkunisquat.wulfpak.core.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.maskedkunisquat.wulfpak.core.data.entity.PersonRelationship
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface PersonRelationshipDao {

    @Query("""
        SELECT * FROM person_relationships
        WHERE personAId = :personId OR personBId = :personId
    """)
    fun getForPerson(personId: UUID): Flow<List<PersonRelationship>>

    @Query("SELECT * FROM person_relationships WHERE personAId = :personId OR personBId = :personId")
    suspend fun getForPersonOnce(personId: UUID): List<PersonRelationship>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(relationship: PersonRelationship)

    @Delete
    suspend fun delete(relationship: PersonRelationship)

    @Query("DELETE FROM person_relationships WHERE personAId = :personAId AND personBId = :personBId")
    suspend fun deletePair(personAId: UUID, personBId: UUID)
}
