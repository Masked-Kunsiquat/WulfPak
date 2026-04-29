package com.github.maskedkunisquat.wulfpak.ui.settings

import android.app.Application
import android.app.DownloadManager
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
import com.github.maskedkunisquat.wulfpak.core.data.entity.RelationLabel
import com.github.maskedkunisquat.wulfpak.core.logic.llm.ModelLoadState
import com.github.maskedkunisquat.wulfpak.sync.CalendarBridge
import com.github.maskedkunisquat.wulfpak.sync.ContactSyncManager
import com.github.maskedkunisquat.wulfpak.sync.VCardImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val appApp             = getApplication<AppApplication>()
    private val contactSyncManager = ContactSyncManager(appApp.db)
    private val vCardImporter      = VCardImporter(appApp.db)
    private val calendarBridge     = CalendarBridge(appApp.db)

    // ── Sync / import states ──────────────────────────────────────────────

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

    // ── Carousel state ────────────────────────────────────────────────────

    data class CarouselContact(
        val candidate: ContactSyncManager.ContactCandidate,
        val assignedRelation: String? = null,
    )

    sealed class CarouselState {
        object Idle    : CarouselState()
        object Loading : CarouselState()
        data class Active(val contacts: List<CarouselContact>, val currentIndex: Int) : CarouselState()
    }

    // ── Observable state ──────────────────────────────────────────────────

    var syncState      by mutableStateOf<SyncState>(SyncState.Idle)         ; private set
    var importState    by mutableStateOf<ImportState>(ImportState.Idle)     ; private set
    var calendarState  by mutableStateOf<CalendarState>(CalendarState.Idle) ; private set
    var carouselState  by mutableStateOf<CarouselState>(CarouselState.Idle) ; private set
    var downloadProgress by mutableStateOf<Int?>(null)                      ; private set

    val biometricEnabled = appApp.appDataStore.data
        .map { it[AppPrefsKeys.BIOMETRIC_ENABLED] ?: true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val modelLoadState = appApp.llmProvider.modelLoadState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ModelLoadState.IDLE)

    val isModelAvailable: Boolean
        get() = appApp.llmProvider.isModelAvailable()

    // ── Security ──────────────────────────────────────────────────────────

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appApp.appDataStore.edit { it[AppPrefsKeys.BIOMETRIC_ENABLED] = enabled }
        }
    }

    // ── AI model ──────────────────────────────────────────────────────────

    fun downloadModel() {
        val downloadId = appApp.llmProvider.downloadModel()
        pollDownloadProgress(downloadId)
    }

    fun loadModel() {
        viewModelScope.launch(Dispatchers.IO) {
            appApp.llmProvider.initialize()
        }
    }

    private fun pollDownloadProgress(downloadId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val dm = getApplication<Application>().getSystemService(DownloadManager::class.java)
            while (true) {
                val query  = DownloadManager.Query().setFilterById(downloadId)
                val cursor = dm.query(query) ?: break
                var shouldBreak = false
                cursor.use { c ->
                    if (!c.moveToFirst()) { downloadProgress = null; shouldBreak = true; return@use }
                    val statusCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                    val totalCol  = c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    val soFarCol  = c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    when (c.getInt(statusCol)) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            downloadProgress = null
                            shouldBreak = true
                            appApp.llmProvider.initialize()
                        }
                        DownloadManager.STATUS_FAILED -> {
                            downloadProgress = null
                            shouldBreak = true
                        }
                        DownloadManager.STATUS_RUNNING -> {
                            val total = c.getLong(totalCol)
                            val soFar = c.getLong(soFarCol)
                            downloadProgress = if (total > 0) ((soFar * 100) / total).toInt() else 0
                        }
                        else -> Unit
                    }
                }
                if (shouldBreak) break
                delay(500)
            }
        }
    }

    // ── Contact import carousel ───────────────────────────────────────────

    fun startCarousel(uris: List<Uri>) {
        if (carouselState is CarouselState.Loading) return
        carouselState = CarouselState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            carouselState = try {
                val candidates = contactSyncManager.fetchByUris(getApplication(), uris)
                    .filter { !it.alreadyImported }
                if (candidates.isEmpty()) CarouselState.Idle
                else CarouselState.Active(candidates.map { CarouselContact(it) }, 0)
            } catch (e: Exception) {
                CarouselState.Idle
            }
        }
    }

    fun carouselAssignAndNext(relation: String) {
        val state = carouselState as? CarouselState.Active ?: return
        val updated = state.contacts.toMutableList()
        updated[state.currentIndex] = updated[state.currentIndex].copy(assignedRelation = relation)
        if (state.currentIndex + 1 < updated.size) {
            carouselState = state.copy(contacts = updated, currentIndex = state.currentIndex + 1)
        } else {
            importCarouselResults(updated)
            carouselState = CarouselState.Idle
        }
    }

    fun carouselSkip() {
        val state = carouselState as? CarouselState.Active ?: return
        if (state.currentIndex + 1 < state.contacts.size) {
            carouselState = state.copy(currentIndex = state.currentIndex + 1)
        } else {
            val toImport = state.contacts.filter { it.assignedRelation != null }
            if (toImport.isNotEmpty()) importCarouselResults(toImport)
            carouselState = CarouselState.Idle
        }
    }

    fun dismissCarousel() { carouselState = CarouselState.Idle }

    private fun importCarouselResults(contacts: List<CarouselContact>) {
        val toImport = contacts.filter { it.assignedRelation != null }
        if (toImport.isEmpty()) return
        syncState = SyncState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            syncState = try {
                val assignments = toImport.map { it.candidate to it.assignedRelation!! }
                val r = contactSyncManager.importWithRelations(getApplication(), assignments)
                SyncState.Done(r.added, r.skipped)
            } catch (e: Exception) {
                SyncState.Error(e.message ?: "Import failed")
            }
        }
    }

    // ── vCard import ──────────────────────────────────────────────────────

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

    // ── Calendar sync ─────────────────────────────────────────────────────

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

    fun clearSyncState()     { syncState     = SyncState.Idle }
    fun clearImportState()   { importState   = ImportState.Idle }
    fun clearCalendarState() { calendarState = CalendarState.Idle }
}
