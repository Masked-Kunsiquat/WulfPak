package com.github.maskedkunisquat.wulfpak.ui.settings

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.maskedkunisquat.wulfpak.AppApplication
import com.github.maskedkunisquat.wulfpak.sync.ContactSyncManager
import com.github.maskedkunisquat.wulfpak.sync.VCardImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val contactSyncManager = ContactSyncManager(getApplication<AppApplication>().db)
    private val vCardImporter      = VCardImporter(getApplication<AppApplication>().db)

    sealed class SyncState {
        object Idle    : SyncState()
        object Loading : SyncState()
        data class Done(val added: Int, val skipped: Int) : SyncState()
        data class Error(val message: String)             : SyncState()
    }

    sealed class ImportState {
        object Idle    : ImportState()
        object Loading : ImportState()
        data class Done(val added: Int)      : ImportState()
        data class Error(val message: String) : ImportState()
    }

    var syncState   by mutableStateOf<SyncState>(SyncState.Idle)
        private set
    var importState by mutableStateOf<ImportState>(ImportState.Idle)
        private set

    fun syncContacts() {
        if (syncState is SyncState.Loading) return
        syncState = SyncState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            syncState = try {
                val r = contactSyncManager.sync(getApplication())
                SyncState.Done(r.added, r.skipped)
            } catch (e: Exception) {
                SyncState.Error(e.message ?: "Sync failed")
            }
        }
    }

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

    fun clearSyncState()   { syncState   = SyncState.Idle }
    fun clearImportState() { importState = ImportState.Idle }
}
