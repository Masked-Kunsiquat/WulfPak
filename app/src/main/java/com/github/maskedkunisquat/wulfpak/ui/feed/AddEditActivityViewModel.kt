package com.github.maskedkunisquat.wulfpak.ui.feed

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.github.maskedkunisquat.wulfpak.AppApplication
import com.github.maskedkunisquat.wulfpak.core.data.entity.Activity
import com.github.maskedkunisquat.wulfpak.core.data.entity.ActivityParticipant
import com.github.maskedkunisquat.wulfpak.core.logic.worker.EmbeddingWorker
import com.github.maskedkunisquat.wulfpak.core.logic.worker.SummaryWorker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.github.maskedkunisquat.wulfpak.AppPrefsKeys
import com.github.maskedkunisquat.wulfpak.appDataStore
import com.github.maskedkunisquat.wulfpak.core.logic.closeness.ClosenessCalculator
import kotlinx.coroutines.launch
import java.util.UUID

class AddEditActivityViewModel(app: Application) : AndroidViewModel(app) {

    private val db = getApplication<AppApplication>().db

    var title       by mutableStateOf("")
    var note        by mutableStateOf("")
    var timestampMs by mutableStateOf(System.currentTimeMillis())
    var selectedIds by mutableStateOf(emptySet<UUID>())

    private val sortByLastName = getApplication<AppApplication>().appDataStore.data
        .map { it[AppPrefsKeys.SORT_BY_LAST_NAME] ?: false }

    val allPersons = combine(db.personDao().getAll(), sortByLastName) { persons, byLast ->
        if (byLast) persons.sortedWith(compareBy({ it.lastName ?: it.firstName }, { it.firstName }))
        else persons.sortedBy { it.firstName }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var existingId: UUID? = null

    fun preselect(personId: UUID) {
        selectedIds = selectedIds + personId
    }

    fun load(activityId: UUID) {
        viewModelScope.launch {
            val a = db.activityDao().getById(activityId) ?: return@launch
            existingId  = a.id
            title       = a.title
            note        = a.body ?: ""
            timestampMs = a.timestamp
            selectedIds = db.activityDao().getParticipantIds(a.id).toSet()
        }
    }

    fun togglePerson(personId: UUID) {
        selectedIds = if (personId in selectedIds) selectedIds - personId else selectedIds + personId
    }

    fun save(onDone: () -> Unit) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val id = existingId
            val activityId: UUID
            var prevParticipantIds = emptySet<UUID>()
            if (id == null) {
                val activity = Activity(
                    timestamp = timestampMs,
                    title     = title.trim(),
                    body      = note.trim().ifEmpty { null },
                )
                db.activityDao().insert(activity)
                activityId = activity.id
            } else {
                db.activityDao().getById(id)?.let { existing ->
                    db.activityDao().update(existing.copy(
                        timestamp = timestampMs,
                        title     = title.trim(),
                        body      = note.trim().ifEmpty { null },
                    ))
                }
                activityId = id
                prevParticipantIds = db.activityDao().getParticipantIds(activityId).toSet()
                prevParticipantIds.forEach { pid ->
                    db.activityDao().deleteParticipant(ActivityParticipant(activityId, pid))
                }
            }
            selectedIds.forEach { personId ->
                db.activityDao().insertParticipant(ActivityParticipant(activityId, personId))
            }
            val wm = WorkManager.getInstance(getApplication())
            EmbeddingWorker.enqueue(wm)
            (prevParticipantIds + selectedIds).forEach { pid ->
                SummaryWorker.enqueue(wm, pid)
                recomputeScore(pid)
            }
            onDone()
        }
    }

    private suspend fun recomputeScore(personId: UUID) {
        val person = db.personDao().getById(personId) ?: return
        val interactions = db.interactionDao().getForPersonOnce(personId)
        val activityTimestamps = db.activityDao().getTimestampsForPerson(personId)
        val score = ClosenessCalculator.compute(interactions, activityTimestamps, ClosenessCalculator.categoryFor(person.relationLabel))
        db.personDao().updateClosenessScore(personId, score)
    }
}
