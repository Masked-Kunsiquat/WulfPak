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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class AddEditActivityViewModel(app: Application) : AndroidViewModel(app) {

    private val db = getApplication<AppApplication>().db

    var title       by mutableStateOf("")
    var note        by mutableStateOf("")
    var timestampMs by mutableStateOf(System.currentTimeMillis())
    var selectedIds by mutableStateOf(emptySet<UUID>())

    val allPersons = db.personDao().getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
                db.activityDao().getParticipantIds(activityId).forEach { pid ->
                    db.activityDao().deleteParticipant(ActivityParticipant(activityId, pid))
                }
            }
            selectedIds.forEach { personId ->
                db.activityDao().insertParticipant(ActivityParticipant(activityId, personId))
            }
            EmbeddingWorker.enqueue(WorkManager.getInstance(getApplication()))
            onDone()
        }
    }
}
