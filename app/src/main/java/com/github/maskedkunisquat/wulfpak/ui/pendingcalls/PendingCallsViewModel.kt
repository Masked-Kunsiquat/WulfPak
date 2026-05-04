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
import com.github.maskedkunisquat.wulfpak.core.data.entity.Interaction
import com.github.maskedkunisquat.wulfpak.core.data.entity.InteractionParticipant
import com.github.maskedkunisquat.wulfpak.core.data.entity.InteractionType
import com.github.maskedkunisquat.wulfpak.core.data.entity.Note
import com.github.maskedkunisquat.wulfpak.model.PendingCallStub
import com.github.maskedkunisquat.wulfpak.model.toJsonString
import com.github.maskedkunisquat.wulfpak.model.toPendingCallStubs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class PendingCallsViewModel(app: Application) : AndroidViewModel(app) {

    private val appApp = getApplication<AppApplication>()

    val pendingStubs = appApp.appDataStore.data
        .map { (it[AppPrefsKeys.PENDING_CALL_STUBS] ?: "").toPendingCallStubs() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var confirmedStubs by mutableStateOf<List<PendingCallStub>>(emptyList()); private set

    fun skip(stub: PendingCallStub) {
        viewModelScope.launch {
            appApp.appDataStore.edit { prefs ->
                val current = (prefs[AppPrefsKeys.PENDING_CALL_STUBS] ?: "").toPendingCallStubs()
                prefs[AppPrefsKeys.PENDING_CALL_STUBS] = current
                    .filter { it.personId != stub.personId || it.timestamp != stub.timestamp }
                    .toJsonString()
            }
        }
    }

    fun confirm(stub: PendingCallStub) {
        viewModelScope.launch(Dispatchers.IO) {
            val db = appApp.db
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
            appApp.appDataStore.edit { prefs ->
                val current = (prefs[AppPrefsKeys.PENDING_CALL_STUBS] ?: "").toPendingCallStubs()
                prefs[AppPrefsKeys.PENDING_CALL_STUBS] = current
                    .filter { it.personId != stub.personId || it.timestamp != stub.timestamp }
                    .toJsonString()
            }
            confirmedStubs = confirmedStubs + stub
        }
    }

    fun saveNote(stub: PendingCallStub, text: String) {
        val trimmed = text.trim()
        if (trimmed.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                appApp.db.noteDao().insert(
                    Note(
                        personId  = UUID.fromString(stub.personId),
                        timestamp = stub.timestamp,
                        body      = trimmed,
                    )
                )
            }
        }
        dismissConfirmed(stub)
    }

    fun dismissConfirmed(stub: PendingCallStub) {
        confirmedStubs = confirmedStubs.filter { it.personId != stub.personId || it.timestamp != stub.timestamp }
    }
}
