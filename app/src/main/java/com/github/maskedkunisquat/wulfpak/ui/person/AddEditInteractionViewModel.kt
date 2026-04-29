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
import com.github.maskedkunisquat.wulfpak.core.logic.worker.EmbeddingWorker
import kotlinx.coroutines.launch
import java.util.UUID

class AddEditInteractionViewModel(app: Application) : AndroidViewModel(app) {

    private val db = getApplication<AppApplication>().db

    var type         by mutableStateOf(InteractionType.IN_PERSON)
    var timestampMs  by mutableStateOf(System.currentTimeMillis())
    var durationMins by mutableStateOf("")
    var note         by mutableStateOf("")

    private var existingId: UUID? = null
    private var personId:   UUID? = null

    fun load(personIdStr: String, interactionId: UUID?) {
        personId = UUID.fromString(personIdStr)
        interactionId ?: return
        viewModelScope.launch {
            val i = db.interactionDao().getById(interactionId) ?: return@launch
            existingId   = i.id
            type         = i.type
            timestampMs  = i.timestamp
            durationMins = i.durationSeconds?.let { (it / 60).toString() } ?: ""
            note         = i.note ?: ""
        }
    }

    fun save(onDone: () -> Unit) {
        val pid = personId ?: return
        viewModelScope.launch {
            val durationSec = durationMins.trim().toIntOrNull()?.times(60)
            val id = existingId
            if (id == null) {
                val interaction = Interaction(
                    timestamp       = timestampMs,
                    type            = type,
                    durationSeconds = durationSec,
                    note            = note.trim().ifEmpty { null },
                )
                db.interactionDao().insert(interaction)
                db.interactionDao().insertParticipant(InteractionParticipant(interaction.id, pid))
                db.personDao().onInteractionAdded(pid, timestampMs)
            } else {
                db.interactionDao().getById(id)?.let { existing ->
                    db.interactionDao().update(existing.copy(
                        timestamp       = timestampMs,
                        type            = type,
                        durationSeconds = durationSec,
                        note            = note.trim().ifEmpty { null },
                    ))
                }
            }
            EmbeddingWorker.enqueue(WorkManager.getInstance(getApplication()))
            onDone()
        }
    }
}
