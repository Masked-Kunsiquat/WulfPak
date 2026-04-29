package com.github.maskedkunisquat.wulfpak.ui.person

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.maskedkunisquat.wulfpak.AppApplication
import com.github.maskedkunisquat.wulfpak.core.data.entity.Gift
import com.github.maskedkunisquat.wulfpak.core.data.entity.GiftStatus
import kotlinx.coroutines.launch
import java.util.UUID

class AddEditGiftViewModel(app: Application) : AndroidViewModel(app) {

    private val db = getApplication<AppApplication>().db

    var name     by mutableStateOf("")
    var occasion by mutableStateOf("")
    var status   by mutableStateOf(GiftStatus.IDEA)
    var note     by mutableStateOf("")

    private var existingId: UUID? = null
    private var personId:   UUID? = null

    val isValid: Boolean get() = name.isNotBlank()

    fun load(personIdStr: String, giftId: UUID?) {
        personId = UUID.fromString(personIdStr)
        giftId ?: return
        viewModelScope.launch {
            val g = db.giftDao().getById(giftId) ?: return@launch
            existingId = g.id
            name       = g.name
            occasion   = g.occasion ?: ""
            status     = g.status
            note       = g.note ?: ""
        }
    }

    fun save(onDone: () -> Unit) {
        if (!isValid) return
        val pid = personId ?: return
        viewModelScope.launch {
            val id = existingId
            if (id == null) {
                db.giftDao().insert(Gift(
                    personId = pid,
                    name     = name.trim(),
                    occasion = occasion.trim().ifEmpty { null },
                    status   = status,
                    note     = note.trim().ifEmpty { null },
                ))
            } else {
                db.giftDao().getById(id)?.let { existing ->
                    db.giftDao().update(existing.copy(
                        name     = name.trim(),
                        occasion = occasion.trim().ifEmpty { null },
                        status   = status,
                        note     = note.trim().ifEmpty { null },
                    ))
                }
            }
            onDone()
        }
    }
}
