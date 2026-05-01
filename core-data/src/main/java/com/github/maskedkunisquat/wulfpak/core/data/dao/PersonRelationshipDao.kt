package com.github.maskedkunisquat.wulfpak.core.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.maskedkunisquat.wulfpak.core.data.entity.PersonRelationship
import kotlinx.coroutines.flow.Flow
import java.util.UUID

data class PersonConnection(
    val otherId: UUID,
    val firstName: String,
    val lastName: String?,
    val nickname: String?,
    val label: String,
    val isPersonA: Boolean,
) {
    // Label as it should read from the current person's perspective.
    // Symmetric labels ("married to", "sibling") read the same both ways.
    // Asymmetric labels ("parent of") need their reverse when viewed from personB's side.
    val effectiveLabel: String get() = if (isPersonA) label else REVERSE_LABELS.getOrDefault(label, label)

    private companion object {
        val REVERSE_LABELS = mapOf(
            "Parent"        to "Child",
            "Child"         to "Parent",
            "Introduced me" to "Introduced by",
            // legacy labels from before the noun-form change
            "parent of"        to "child of",
            "child of"         to "parent of",
            "introduced me to" to "was introduced by",
        )
    }
}

@Dao
interface PersonRelationshipDao {

    @Query("""
        SELECT pr.personBId AS otherId, p.firstName, p.lastName, p.nickname, pr.label, 1 AS isPersonA
        FROM person_relationships pr JOIN persons p ON p.id = pr.personBId
        WHERE pr.personAId = :personId
        UNION ALL
        SELECT pr.personAId AS otherId, p.firstName, p.lastName, p.nickname, pr.label, 0 AS isPersonA
        FROM person_relationships pr JOIN persons p ON p.id = pr.personAId
        WHERE pr.personBId = :personId
    """)
    fun getConnectionsForPerson(personId: UUID): Flow<List<PersonConnection>>

    @Query("""
        SELECT pr.personBId AS otherId, p.firstName, p.lastName, p.nickname, pr.label, 1 AS isPersonA
        FROM person_relationships pr JOIN persons p ON p.id = pr.personBId
        WHERE pr.personAId = :personId
        UNION ALL
        SELECT pr.personAId AS otherId, p.firstName, p.lastName, p.nickname, pr.label, 0 AS isPersonA
        FROM person_relationships pr JOIN persons p ON p.id = pr.personAId
        WHERE pr.personBId = :personId
    """)
    suspend fun getConnectionsForPersonOnce(personId: UUID): List<PersonConnection>

    @Query("SELECT * FROM person_relationships WHERE personAId = :personId OR personBId = :personId")
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
