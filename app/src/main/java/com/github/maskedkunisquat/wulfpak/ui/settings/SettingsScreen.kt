package com.github.maskedkunisquat.wulfpak.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.maskedkunisquat.wulfpak.core.logic.llm.ModelLoadState
import com.github.maskedkunisquat.wulfpak.sync.ContactSyncManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateMerge: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val biometricEnabled by viewModel.biometricEnabled.collectAsStateWithLifecycle()
    val modelLoadState   by viewModel.modelLoadState.collectAsStateWithLifecycle()

    val contactPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.loadContactCandidates() }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms[Manifest.permission.WRITE_CALENDAR] == true) viewModel.syncCalendar()
    }

    val vCardPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.importVCard(it) } }

    LaunchedEffect(viewModel.syncState) {
        when (val s = viewModel.syncState) {
            is SettingsViewModel.SyncState.Done -> {
                snackbarHostState.showSnackbar(
                    "Added ${s.added} contact${if (s.added == 1) "" else "s"}, ${s.skipped} already existed"
                )
                viewModel.clearSyncState()
            }
            is SettingsViewModel.SyncState.Error -> {
                snackbarHostState.showSnackbar("Sync failed: ${s.message}")
                viewModel.clearSyncState()
            }
            else -> Unit
        }
    }

    LaunchedEffect(viewModel.importState) {
        when (val s = viewModel.importState) {
            is SettingsViewModel.ImportState.Done -> {
                snackbarHostState.showSnackbar(
                    "Imported ${s.added} contact${if (s.added == 1) "" else "s"} from vCard"
                )
                viewModel.clearImportState()
            }
            is SettingsViewModel.ImportState.Error -> {
                snackbarHostState.showSnackbar("Import failed: ${s.message}")
                viewModel.clearImportState()
            }
            else -> Unit
        }
    }

    LaunchedEffect(viewModel.calendarState) {
        when (val s = viewModel.calendarState) {
            is SettingsViewModel.CalendarState.Done -> {
                snackbarHostState.showSnackbar(
                    "Added ${s.added} event${if (s.added == 1) "" else "s"}, ${s.skipped} already existed"
                )
                viewModel.clearCalendarState()
            }
            is SettingsViewModel.CalendarState.Error -> {
                snackbarHostState.showSnackbar("Calendar sync failed: ${s.message}")
                viewModel.clearCalendarState()
            }
            else -> Unit
        }
    }

    LaunchedEffect(viewModel.pickerState) {
        if (viewModel.pickerState is SettingsViewModel.PickerState.Error) {
            snackbarHostState.showSnackbar(
                "Failed to load contacts: ${(viewModel.pickerState as SettingsViewModel.PickerState.Error).message}"
            )
            viewModel.dismissContactPicker()
        }
    }

    val pickerState = viewModel.pickerState
    if (pickerState is SettingsViewModel.PickerState.Ready) {
        ContactPickerDialog(
            candidates = pickerState.candidates,
            onConfirm  = { viewModel.importSelectedContacts(it) },
            onDismiss  = { viewModel.dismissContactPicker() },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {

            item { SectionHeader("Security") }
            item {
                ListItem(
                    headlineContent   = { Text("Biometric lock") },
                    supportingContent = { Text("Re-lock when app goes to background") },
                    leadingContent    = { Icon(Icons.Default.Fingerprint, contentDescription = null) },
                    trailingContent   = {
                        Switch(
                            checked = biometricEnabled,
                            onCheckedChange = { viewModel.setBiometricEnabled(it) },
                        )
                    },
                )
            }

            item { SectionHeader("AI features") }
            item {
                val modelReady      = modelLoadState == ModelLoadState.READY
                val modelLoading    = modelLoadState == ModelLoadState.LOADING_SESSION
                val modelAvailable  = viewModel.isModelAvailable
                ListItem(
                    headlineContent = {
                        Text(when {
                            modelReady     -> "AI model ready"
                            modelLoading   -> "Loading model…"
                            modelAvailable -> "AI model downloaded"
                            else           -> "Download AI model"
                        })
                    },
                    supportingContent = {
                        Text(when {
                            modelReady     -> "On-device Gemma 3 1B is loaded and ready"
                            modelAvailable -> "Tap to load into memory"
                            else           -> "Gemma 3 1B (~1 GB) — required for AI features"
                        })
                    },
                    leadingContent = { Icon(Icons.Default.Download, contentDescription = null) },
                    trailingContent = {
                        if (modelLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    },
                    modifier = Modifier.clickable(enabled = !modelReady && !modelLoading) {
                        if (modelAvailable) {
                            // file exists but not loaded — LlmOrchestrator calls initialize() on first use
                        } else {
                            viewModel.downloadModel()
                        }
                    },
                )
            }

            item { SectionHeader("Contacts") }
            item {
                ListItem(
                    headlineContent   = { Text("Resolve duplicate contacts") },
                    supportingContent = { Text("Merge contacts that were imported with the same name") },
                    leadingContent    = { Icon(Icons.Default.Group, contentDescription = null) },
                    modifier          = Modifier.clickable { onNavigateMerge() },
                )
            }

            item { SectionHeader("Data import") }
            item {
                val isLoadingPicker = viewModel.pickerState is SettingsViewModel.PickerState.Loading
                val isSyncing       = viewModel.syncState is SettingsViewModel.SyncState.Loading
                ListItem(
                    headlineContent   = { Text("Import from Contacts") },
                    supportingContent = { Text("Choose contacts from your device to add to WulfPak") },
                    leadingContent    = { Icon(Icons.Default.Sync, contentDescription = null) },
                    trailingContent   = {
                        if (isLoadingPicker || isSyncing) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    },
                    modifier = Modifier.clickable(enabled = !isLoadingPicker && !isSyncing) {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
                            == PackageManager.PERMISSION_GRANTED
                        ) {
                            viewModel.loadContactCandidates()
                        } else {
                            contactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                        }
                    },
                )
            }
            item {
                val isImporting = viewModel.importState is SettingsViewModel.ImportState.Loading
                ListItem(
                    headlineContent   = { Text("Import vCard file") },
                    supportingContent = { Text("Import contacts from a .vcf file") },
                    leadingContent    = { Icon(Icons.Default.FileOpen, contentDescription = null) },
                    trailingContent   = {
                        if (isImporting) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    },
                    modifier = Modifier.clickable(enabled = !isImporting) {
                        vCardPickerLauncher.launch("text/vcard")
                    },
                )
            }
            item {
                val isSyncing = viewModel.calendarState is SettingsViewModel.CalendarState.Loading
                ListItem(
                    headlineContent   = { Text("Export to Calendar") },
                    supportingContent = { Text("Add life events (birthdays, anniversaries) to device calendar") },
                    leadingContent    = { Icon(Icons.Default.DateRange, contentDescription = null) },
                    trailingContent   = {
                        if (isSyncing) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    },
                    modifier = Modifier.clickable(enabled = !isSyncing) {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR)
                            == PackageManager.PERMISSION_GRANTED
                        ) {
                            viewModel.syncCalendar()
                        } else {
                            calendarPermissionLauncher.launch(
                                arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ContactPickerDialog(
    candidates: List<ContactSyncManager.ContactCandidate>,
    onConfirm: (List<ContactSyncManager.ContactCandidate>) -> Unit,
    onDismiss: () -> Unit,
) {
    val importable = remember(candidates) { candidates.filter { !it.alreadyImported } }
    var selected by remember { mutableStateOf(emptySet<String>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Contacts to Import") },
        text = {
            if (importable.isEmpty()) {
                Text("All contacts from this device are already in WulfPak.")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(importable, key = { it.contactId }) { candidate ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = if (candidate.contactId in selected)
                                        selected - candidate.contactId
                                    else
                                        selected + candidate.contactId
                                }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = candidate.contactId in selected,
                                onCheckedChange = { checked ->
                                    selected = if (checked)
                                        selected + candidate.contactId
                                    else
                                        selected - candidate.contactId
                                },
                            )
                            Text(
                                text     = candidate.displayName,
                                modifier = Modifier.padding(start = 4.dp),
                                style    = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(importable.filter { it.contactId in selected })
                },
                enabled = selected.isNotEmpty(),
            ) {
                Text(if (selected.isEmpty()) "Import" else "Import (${selected.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text     = text,
        style    = MaterialTheme.typography.labelSmall,
        color    = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}
