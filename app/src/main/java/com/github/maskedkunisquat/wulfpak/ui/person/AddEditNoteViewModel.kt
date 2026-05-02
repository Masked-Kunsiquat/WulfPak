package com.github.maskedkunisquat.wulfpak.ui.person

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.github.maskedkunisquat.wulfpak.AppApplication
import com.github.maskedkunisquat.wulfpak.core.data.entity.Note
import com.github.maskedkunisquat.wulfpak.core.logic.worker.EmbeddingWorker
import com.github.maskedkunisquat.wulfpak.core.logic.worker.SummaryWorker
import kotlinx.coroutines.launch
import java.util.UUID

class AddEditNoteViewModel(app: Application) : AndroidViewModel(app) {

    private val db = getApplication<AppApplication>().db

    var body by mutableStateOf("")

    private var existingId: UUID? = null
    private var personId:   UUID? = null

    val isValid: Boolean get() = body.isNotBlank()

    fun load(personIdStr: String, noteId: UUID?) {
        personId = UUID.fromString(personIdStr)
        noteId ?: return
        viewModelScope.launch {
            val n = db.noteDao().getById(noteId) ?: return@launch
            existingId = n.id
            body       = n.body
        }
    }

    fun save(onDone: () -> Unit) {
        if (!isValid) return
        val pid = personId ?: return
        viewModelScope.launch {
            val id = existingId
            if (id == null) {
                db.noteDao().insert(Note(
                    personId  = pid,
                    timestamp = System.currentTimeMillis(),
                    body      = body.trim(),
                ))
            } else {
                db.noteDao().getById(id)?.let { existing ->
                    db.noteDao().update(existing.copy(body = body.trim()))
                }
            }
            val wm = WorkManager.getInstance(getApplication())
            EmbeddingWorker.enqueue(wm)
            SummaryWorker.enqueue(wm, pid)
            onDone()
        }
    }
}
