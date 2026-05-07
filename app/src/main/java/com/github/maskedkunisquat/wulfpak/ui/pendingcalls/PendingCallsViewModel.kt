package com.github.maskedkunisquat.wulfpak.ui.pendingcalls

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.maskedkunisquat.wulfpak.AppApplication
import com.github.maskedkunisquat.wulfpak.AppPrefsKeys
import com.github.maskedkunisquat.wulfpak.appDataStore
import com.github.maskedkunisquat.wulfpak.core.logic.debug.DebugEvent
import com.github.maskedkunisquat.wulfpak.core.data.entity.Interaction
import com.github.maskedkunisquat.wulfpak.core.data.entity.InteractionParticipant
import com.github.maskedkunisquat.wulfpak.core.data.entity.InteractionType
import com.github.maskedkunisquat.wulfpak.model.PendingCallStub
import java.util.concurrent.ConcurrentHashMap
import com.github.maskedkunisquat.wulfpak.model.toJsonString
import com.github.maskedkunisquat.wulfpak.model.toPendingCallStubs
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class PendingCallsViewModel(app: Application) : AndroidViewModel(app) {

    private val appApp = getApplication<AppApplication>()

    val pendingStubs = appApp.appDataStore.data
        .map { (it[AppPrefsKeys.PENDING_CALL_STUBS] ?: "").toPendingCallStubs() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var confirmedStubs by mutableStateOf<List<PendingCallStub>>(emptyList()); private set
    private val confirmedInteractionIds = ConcurrentHashMap<Pair<String, Long>, UUID>()

    fun skip(stub: PendingCallStub) {
        viewModelScope.launch {
            appApp.appDataStore.edit { prefs ->
                val current = (prefs[AppPrefsKeys.PENDING_CALL_STUBS] ?: "").toPendingCallStubs()
                prefs[AppPrefsKeys.PENDING_CALL_STUBS] = current
                    .filter { it.personId != stub.personId || it.timestamp != stub.timestamp }
                    .toJsonString()
            }
            appApp.debugEventLogger.log(DebugEvent.PendingCallAction(action = "SKIP", callType = stub.callType))
        }
    }

    fun confirm(stub: PendingCallStub) {
        viewModelScope.launch(Dispatchers.IO) {
            val db = appApp.db
            db.withTransaction {
                val interaction = Interaction(
                    timestamp       = stub.timestamp,
                    type            = InteractionType.CALL,
                    durationSeconds = stub.durationSeconds,
                )
                db.interactionDao().insert(interaction)
                db.interactionDao().insertParticipant(
                    InteractionParticipant(
                        interactionId = interaction.id,
                        personId      = UUID.fromString(stub.personId),
                    )
                )
                db.personDao().onInteractionAdded(UUID.fromString(stub.personId), stub.timestamp)
                confirmedInteractionIds[stub.personId to stub.timestamp] = interaction.id
            }
            appApp.appDataStore.edit { prefs ->
                val current = (prefs[AppPrefsKeys.PENDING_CALL_STUBS] ?: "").toPendingCallStubs()
                prefs[AppPrefsKeys.PENDING_CALL_STUBS] = current
                    .filter { it.personId != stub.personId || it.timestamp != stub.timestamp }
                    .toJsonString()
            }
            appApp.debugEventLogger.log(DebugEvent.PendingCallAction(action = "CONFIRM", callType = stub.callType))
            withContext(Dispatchers.Main) {
                confirmedStubs = confirmedStubs + stub
            }
        }
    }

    fun saveNote(stub: PendingCallStub, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            dismissConfirmed(stub)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val interactionId = confirmedInteractionIds[stub.personId to stub.timestamp] ?: return@launch
                val interaction   = appApp.db.interactionDao().getById(interactionId)         ?: return@launch
                appApp.db.interactionDao().update(interaction.copy(note = trimmed))
                appApp.debugEventLogger.log(DebugEvent.PendingCallAction(action = "SAVE_NOTE", callType = stub.callType))
                withContext(Dispatchers.Main) { dismissConfirmed(stub) }
            } catch (_: Exception) {
                // update failed — confirmation stays visible so the user can retry
            }
        }
    }

    fun dismissWithoutNote(stub: PendingCallStub) {
        appApp.debugEventLogger.log(DebugEvent.PendingCallAction(action = "DISMISS", callType = stub.callType))
        dismissConfirmed(stub)
    }

    fun dismissConfirmed(stub: PendingCallStub) {
        confirmedStubs = confirmedStubs.filter { it.personId != stub.personId || it.timestamp != stub.timestamp }
        confirmedInteractionIds.remove(stub.personId to stub.timestamp)
    }
}
