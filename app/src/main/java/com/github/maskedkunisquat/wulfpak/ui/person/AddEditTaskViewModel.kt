package com.github.maskedkunisquat.wulfpak.ui.person

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.maskedkunisquat.wulfpak.AppApplication
import com.github.maskedkunisquat.wulfpak.core.data.entity.Task
import kotlinx.coroutines.launch
import java.util.UUID

class AddEditTaskViewModel(app: Application) : AndroidViewModel(app) {

    private val db = getApplication<AppApplication>().db

    var title  by mutableStateOf("")
    var dueAtMs by mutableStateOf<Long?>(null)
    var isDone by mutableStateOf(false)

    private var existingId: UUID? = null
    private var personId:   UUID? = null

    val isValid: Boolean get() = title.isNotBlank()

    fun load(personIdStr: String?, taskId: UUID?) {
        personId = personIdStr?.let { UUID.fromString(it) }
        taskId ?: return
        viewModelScope.launch {
            val t = db.taskDao().getById(taskId) ?: return@launch
            existingId = t.id
            title      = t.title
            dueAtMs    = t.dueAt
            isDone     = t.isDone
        }
    }

    fun save(onDone: () -> Unit) {
        if (!isValid) return
        viewModelScope.launch {
            val id = existingId
            if (id == null) {
                db.taskDao().insert(Task(
                    personId = personId,
                    title    = title.trim(),
                    dueAt    = dueAtMs,
                    isDone   = isDone,
                ))
            } else {
                db.taskDao().getById(id)?.let { existing ->
                    db.taskDao().update(existing.copy(
                        title  = title.trim(),
                        dueAt  = dueAtMs,
                        isDone = isDone,
                    ))
                }
            }
            onDone()
        }
    }
}
