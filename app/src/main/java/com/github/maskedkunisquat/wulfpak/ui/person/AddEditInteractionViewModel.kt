package com.github.maskedkunisquat.wulfpak.ui.person

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.github.maskedkunisquat.wulfpak.AppApplication
import com.github.maskedkunisquat.wulfpak.core.data.entity.Interaction
import com.github.maskedkunisquat.wulfpak.core.data.entity.InteractionParticipant
import com.github.maskedkunisquat.wulfpak.core.data.entity.InteractionType
import com.github.maskedkunisquat.wulfpak.core.logic.closeness.ClosenessCalculator
import com.github.maskedkunisquat.wulfpak.core.logic.worker.EmbeddingWorker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.github.maskedkunisquat.wulfpak.AppPrefsKeys
import com.github.maskedkunisquat.wulfpak.appDataStore
import androidx.room.withTransaction
import kotlinx.coroutines.launch
import java.util.UUID

class AddEditInteractionViewModel(app: Application) : AndroidViewModel(app) {

    private val db = getApplication<AppApplication>().db

    var type         by mutableStateOf(InteractionType.IN_PERSON)
    var timestampMs  by mutableStateOf(System.currentTimeMillis())
    var durationMins by mutableStateOf("")
    var note         by mutableStateOf("")
    var selectedIds  by mutableStateOf(emptySet<UUID>())

    private val sortByLastName = getApplication<AppApplication>().appDataStore.data
        .map { it[AppPrefsKeys.SORT_BY_LAST_NAME] ?: false }

    val allPersons = combine(db.personDao().getAll(), sortByLastName) { persons, byLast ->
        if (byLast) persons.sortedWith(compareBy({ it.lastName ?: it.firstName }, { it.firstName }))
        else persons.sortedBy { it.firstName }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var existingId: UUID? = null
    private var personId:   UUID? = null

    private suspend fun recomputeScore(personId: UUID) {
        val person = db.personDao().getById(personId) ?: return
        val interactions = db.interactionDao().getForPersonOnce(personId)
        val score = ClosenessCalculator.compute(interactions, ClosenessCalculator.categoryFor(person.relationLabel))
        db.personDao().updateClosenessScore(personId, score)
    }

    fun togglePerson(personId: UUID) {
        selectedIds = if (personId in selectedIds) selectedIds - personId else selectedIds + personId
    }

    fun load(personIdStr: String, interactionId: UUID?) {
        val pid = UUID.fromString(personIdStr)
        personId = pid
        if (interactionId == null) {
            selectedIds = setOf(pid)
            return
        }
        viewModelScope.launch {
            val i = db.interactionDao().getById(interactionId) ?: return@launch
            existingId   = i.id
            type         = i.type
            timestampMs  = i.timestamp
            durationMins = i.durationSeconds?.let { (it / 60).toString() } ?: ""
            note         = i.note ?: ""
            selectedIds  = db.interactionDao().getParticipantIds(interactionId).toSet()
        }
    }

    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            val effectiveIds = selectedIds.filterNot { it == UUID(0L, 0L) }.toSet()
            val durationSec = durationMins.trim().toIntOrNull()?.times(60)
            val id = existingId
            val interactionId: UUID
            if (id == null) {
                val interaction = Interaction(
                    timestamp       = timestampMs,
                    type            = type,
                    durationSeconds = durationSec,
                    note            = note.trim().ifEmpty { null },
                )
                interactionId = interaction.id
                db.withTransaction {
                    db.interactionDao().insert(interaction)
                    effectiveIds.forEach { sid -> db.personDao().onInteractionAdded(sid, timestampMs) }
                    effectiveIds.forEach { sid ->
                        db.interactionDao().insertParticipant(InteractionParticipant(interactionId, sid))
                    }
                }
            } else {
                interactionId = id
                var removedIds = emptySet<UUID>()
                db.withTransaction {
                    db.interactionDao().getById(id)?.let { existing ->
                        db.interactionDao().update(existing.copy(
                            timestamp       = timestampMs,
                            type            = type,
                            durationSeconds = durationSec,
                            note            = note.trim().ifEmpty { null },
                        ))
                    }
                    val oldIds = db.interactionDao().getParticipantIds(id).toSet()
                    oldIds.forEach { pid ->
                        db.interactionDao().deleteParticipant(InteractionParticipant(id, pid))
                    }
                    val removed = oldIds - effectiveIds
                    val added   = effectiveIds - oldIds
                    removedIds = removed
                    removed.forEach { pid -> db.personDao().onInteractionDeleted(pid) }
                    added.forEach   { pid -> db.personDao().onInteractionAdded(pid, timestampMs) }
                    effectiveIds.forEach { sid ->
                        db.interactionDao().insertParticipant(InteractionParticipant(id, sid))
                    }
                }
                removedIds.forEach { rid -> recomputeScore(rid) }
            }
            effectiveIds.forEach { sid -> recomputeScore(sid) }
            EmbeddingWorker.enqueue(WorkManager.getInstance(getApplication()))
            onDone()
        }
    }
}
