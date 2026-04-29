package com.github.maskedkunisquat.wulfpak.ui.person

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.maskedkunisquat.wulfpak.AppApplication
import com.github.maskedkunisquat.wulfpak.core.data.entity.LifeEvent
import com.github.maskedkunisquat.wulfpak.core.data.entity.LifeEventType
import kotlinx.coroutines.launch
import java.util.UUID

class AddEditLifeEventViewModel(app: Application) : AndroidViewModel(app) {

    private val db = getApplication<AppApplication>().db

    var eventType   by mutableStateOf(LifeEventType.BIRTHDAY)
    var dateMs      by mutableStateOf(System.currentTimeMillis())
    var isRecurring by mutableStateOf(false)
    var note        by mutableStateOf("")

    private var existingId: UUID? = null
    private var personId:   UUID? = null

    fun load(personIdStr: String, lifeEventId: UUID?) {
        personId = UUID.fromString(personIdStr)
        lifeEventId ?: return
        viewModelScope.launch {
            val e = db.lifeEventDao().getById(lifeEventId) ?: return@launch
            existingId  = e.id
            eventType   = e.eventType
            dateMs      = e.date
            isRecurring = e.isRecurring
            note        = e.note ?: ""
        }
    }

    fun save(onDone: () -> Unit) {
        val pid = personId ?: return
        viewModelScope.launch {
            val id = existingId
            if (id == null) {
                db.lifeEventDao().insert(LifeEvent(
                    personId    = pid,
                    eventType   = eventType,
                    date        = dateMs,
                    isRecurring = isRecurring,
                    note        = note.trim().ifEmpty { null },
                ))
            } else {
                db.lifeEventDao().getById(id)?.let { existing ->
                    db.lifeEventDao().update(existing.copy(
                        eventType   = eventType,
                        date        = dateMs,
                        isRecurring = isRecurring,
                        note        = note.trim().ifEmpty { null },
                    ))
                }
            }
            onDone()
        }
    }
}
