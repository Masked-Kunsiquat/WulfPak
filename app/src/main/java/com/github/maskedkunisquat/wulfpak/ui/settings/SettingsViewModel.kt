package com.github.maskedkunisquat.wulfpak.ui.settings

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.maskedkunisquat.wulfpak.AppApplication
import com.github.maskedkunisquat.wulfpak.AppPrefsKeys
import com.github.maskedkunisquat.wulfpak.appDataStore
import com.github.maskedkunisquat.wulfpak.core.logic.llm.ModelLoadState
import com.github.maskedkunisquat.wulfpak.sync.CalendarBridge
import com.github.maskedkunisquat.wulfpak.sync.ContactSyncManager
import com.github.maskedkunisquat.wulfpak.sync.VCardImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val appApp             = getApplication<AppApplication>()
    private val contactSyncManager = ContactSyncManager(appApp.db)
    private val vCardImporter      = VCardImporter(appApp.db)
    private val calendarBridge     = CalendarBridge(appApp.db)

    sealed class SyncState {
        object Idle    : SyncState()
        object Loading : SyncState()
        data class Done(val added: Int, val skipped: Int) : SyncState()
        data class Error(val message: String)             : SyncState()
    }

    sealed class ImportState {
        object Idle    : ImportState()
        object Loading : ImportState()
        data class Done(val added: Int)       : ImportState()
        data class Error(val message: String) : ImportState()
    }

    sealed class CalendarState {
        object Idle    : CalendarState()
        object Loading : CalendarState()
        data class Done(val added: Int, val skipped: Int) : CalendarState()
        data class Error(val message: String)              : CalendarState()
    }

    sealed class PickerState {
        object Hidden  : PickerState()
        object Loading : PickerState()
        data class Ready(val candidates: List<ContactSyncManager.ContactCandidate>) : PickerState()
        data class Error(val message: String) : PickerState()
    }

    var syncState     by mutableStateOf<SyncState>(SyncState.Idle)
        private set
    var importState   by mutableStateOf<ImportState>(ImportState.Idle)
        private set
    var calendarState by mutableStateOf<CalendarState>(CalendarState.Idle)
        private set
    var pickerState   by mutableStateOf<PickerState>(PickerState.Hidden)
        private set

    val biometricEnabled = appApp.appDataStore.data
        .map { it[AppPrefsKeys.BIOMETRIC_ENABLED] ?: true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appApp.appDataStore.edit { it[AppPrefsKeys.BIOMETRIC_ENABLED] = enabled }
        }
    }

    val modelLoadState = appApp.llmProvider.modelLoadState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ModelLoadState.IDLE)

    val isModelAvailable: Boolean
        get() = appApp.llmProvider.isModelAvailable()

    fun downloadModel() {
        appApp.llmProvider.downloadModel()
    }

    fun loadContactCandidates() {
        if (pickerState is PickerState.Loading) return
        pickerState = PickerState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            pickerState = try {
                val candidates = contactSyncManager.fetchCandidates(getApplication())
                PickerState.Ready(candidates)
            } catch (e: Exception) {
                PickerState.Error(e.message ?: "Failed to load contacts")
            }
        }
    }

    fun importSelectedContacts(selected: List<ContactSyncManager.ContactCandidate>) {
        pickerState = PickerState.Hidden
        if (syncState is SyncState.Loading) return
        syncState = SyncState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            syncState = try {
                val r = contactSyncManager.importSelected(getApplication(), selected)
                SyncState.Done(r.added, r.skipped)
            } catch (e: Exception) {
                SyncState.Error(e.message ?: "Import failed")
            }
        }
    }

    fun dismissContactPicker() { pickerState = PickerState.Hidden }

    fun importVCard(uri: Uri) {
        if (importState is ImportState.Loading) return
        importState = ImportState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            importState = try {
                val stream = getApplication<Application>().contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Cannot open file")
                stream.use { vCardImporter.import(it) }.let { ImportState.Done(it.added) }
            } catch (e: Exception) {
                ImportState.Error(e.message ?: "Import failed")
            }
        }
    }

    fun syncCalendar() {
        if (calendarState is CalendarState.Loading) return
        calendarState = CalendarState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            calendarState = try {
                val r = calendarBridge.sync(getApplication())
                CalendarState.Done(r.added, r.skipped)
            } catch (e: Exception) {
                CalendarState.Error(e.message ?: "Calendar sync failed")
            }
        }
    }

    fun clearSyncState()      { syncState     = SyncState.Idle }
    fun clearImportState()    { importState   = ImportState.Idle }
    fun clearCalendarState()  { calendarState = CalendarState.Idle }
}
