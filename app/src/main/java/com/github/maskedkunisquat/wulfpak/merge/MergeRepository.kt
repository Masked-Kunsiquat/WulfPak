package com.github.maskedkunisquat.wulfpak.merge

import androidx.room.withTransaction
import com.github.maskedkunisquat.wulfpak.core.data.AppDatabase
import com.github.maskedkunisquat.wulfpak.core.data.entity.Person
import com.github.maskedkunisquat.wulfpak.core.data.entity.PersonRelationship
import java.util.UUID

class MergeRepository(private val db: AppDatabase) {

    suspend fun findDuplicates(): List<Pair<Person, Person>> =
        db.personDao().getAllOnce()
            .groupBy { normalizeKey(it.firstName, it.lastName) }
            .values
            .filter { it.size > 1 }
            .flatMap { group ->
                group.sortedByDescending { it.interactionCount }
                    .chunked(2)
                    .mapNotNull { chunk -> if (chunk.size == 2) chunk[0] to chunk[1] else null }
            }

    suspend fun merge(keepId: UUID, discardId: UUID) = db.withTransaction {
        // 1. ContactDetails: copy unique values to keep, skip value duplicates
        val keepValues = db.contactDetailDao().getForPersonOnce(keepId).map { it.value }.toSet()
        db.contactDetailDao().getForPersonOnce(discardId).forEach { detail ->
            if (detail.value !in keepValues) {
                db.contactDetailDao().insert(detail.copy(id = UUID.randomUUID(), personId = keepId))
            }
        }

        // 2. Bulk reassign all direct-FK entity types
        db.lifeEventDao().reassignToPerson(fromId = discardId, toId = keepId)
        db.noteDao().reassignToPerson(fromId = discardId, toId = keepId)
        db.giftDao().reassignToPerson(fromId = discardId, toId = keepId)
        db.taskDao().reassignToPerson(fromId = discardId, toId = keepId)

        // 3. Junction tables: re-insert participant rows under keepId (IGNORE handles conflicts
        //    when both persons were already in the same interaction/activity)
        db.interactionDao().getParticipantsByPerson(discardId).forEach { ip ->
            db.interactionDao().insertParticipant(ip.copy(personId = keepId))
        }
        db.activityDao().getParticipantsByPerson(discardId).forEach { ap ->
            db.activityDao().insertParticipant(ap.copy(personId = keepId))
        }

        // 4. PersonRelationships: rewrite edges that pointed to discard; drop self-loops
        db.personRelationshipDao().getForPersonOnce(discardId).forEach { rel ->
            db.personRelationshipDao().delete(rel)
            val newA = if (rel.personAId == discardId) keepId else rel.personAId
            val newB = if (rel.personBId == discardId) keepId else rel.personBId
            if (newA != newB) {
                db.personRelationshipDao().insert(PersonRelationship(newA, newB, rel.label))
            }
        }

        // 5. Merge denormalized stats; preserve best non-null fields from discard
        val keep    = db.personDao().getById(keepId)!!
        val discard = db.personDao().getById(discardId)!!
        db.personDao().update(keep.copy(
            lastContactedAt  = maxOf(keep.lastContactedAt ?: 0L, discard.lastContactedAt ?: 0L)
                                   .takeIf { it > 0L },
            interactionCount = keep.interactionCount + discard.interactionCount,
            photoUri         = keep.photoUri ?: discard.photoUri,
            nickname         = keep.nickname ?: discard.nickname,
            closenessRating  = keep.closenessRating ?: discard.closenessRating,
        ))

        // 6. Delete discard — CASCADE handles any remaining linked rows
        db.personDao().delete(discard)
    }

    private fun normalizeKey(first: String, last: String?) =
        "${first.trim().lowercase()}|${last?.trim()?.lowercase() ?: ""}"
}
