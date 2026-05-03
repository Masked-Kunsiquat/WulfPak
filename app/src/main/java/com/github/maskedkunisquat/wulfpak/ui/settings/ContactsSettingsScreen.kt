package com.github.maskedkunisquat.wulfpak.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.github.maskedkunisquat.wulfpak.core.data.entity.RelationLabel
import com.github.maskedkunisquat.wulfpak.ui.common.toDisplayLabel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsSettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateMerge: () -> Unit,
    onNavigateContactPick: () -> Unit,
    viewModel: SettingsViewModel,
) {
    val context           = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var pendingImportUri    by remember { mutableStateOf<Uri?>(null) }
    var showImportConfirm   by remember { mutableStateOf(false) }

    val backupExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportBackup(it) } }

    val backupImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { pendingImportUri = it; showImportConfirm = true } }

    val contactPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) onNavigateContactPick() }

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

    LaunchedEffect(viewModel.carouselMessage) {
        viewModel.carouselMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearCarouselMessage()
        }
    }

    LaunchedEffect(viewModel.backupState) {
        when (val s = viewModel.backupState) {
            is SettingsViewModel.BackupState.ExportDone -> {
                snackbarHostState.showSnackbar("Backup exported successfully")
                viewModel.clearBackupState()
            }
            is SettingsViewModel.BackupState.ImportDone -> {
                snackbarHostState.showSnackbar(
                    "Restored ${s.personCount} people from backup"
                )
                viewModel.clearBackupState()
            }
            is SettingsViewModel.BackupState.Error -> {
                snackbarHostState.showSnackbar("Backup error: ${s.message}")
                viewModel.clearBackupState()
            }
            else -> Unit
        }
    }

    if (showImportConfirm) {
        AlertDialog(
            onDismissRequest = { showImportConfirm = false },
            title = { Text("Replace all data?") },
            text  = { Text("This will permanently delete all current data and replace it with the backup. This cannot be undone.") },
            confirmButton = {
                Button(onClick = {
                    showImportConfirm = false
                    pendingImportUri?.let { viewModel.importBackup(it) }
                }) { Text("Replace") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showImportConfirm = false }) { Text("Cancel") }
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Contacts") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            Column(modifier = Modifier.padding(padding)) {
                val isExporting = viewModel.backupState is SettingsViewModel.BackupState.Exporting
                ListItem(
                    headlineContent   = { Text("Export backup") },
                    supportingContent = { Text("Save all your data to a JSON file") },
                    leadingContent    = { Icon(Icons.Default.SaveAlt, contentDescription = null) },
                    trailingContent   = {
                        if (isExporting) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    },
                    modifier = Modifier.clickable(enabled = !isExporting) {
                        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                        backupExportLauncher.launch("wulfpak_backup_$ts.json")
                    },
                )

                val isRestoring = viewModel.backupState is SettingsViewModel.BackupState.Importing
                ListItem(
                    headlineContent   = { Text("Import backup") },
                    supportingContent = { Text("Restore data from a backup file") },
                    leadingContent    = { Icon(Icons.Default.Restore, contentDescription = null) },
                    trailingContent   = {
                        if (isRestoring) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    },
                    modifier = Modifier.clickable(enabled = !isRestoring) {
                        backupImportLauncher.launch(arrayOf("application/json", "*/*"))
                    },
                )

                ListItem(
                    headlineContent   = { Text("Resolve duplicate contacts") },
                    supportingContent = { Text("Merge contacts that were imported with the same name") },
                    leadingContent    = { Icon(Icons.Default.Group, contentDescription = null) },
                    modifier          = Modifier.clickable { onNavigateMerge() },
                )

                val isImportingContacts = viewModel.syncState is SettingsViewModel.SyncState.Loading
                ListItem(
                    headlineContent   = { Text("Import from Contacts") },
                    supportingContent = { Text("Pick contacts from your device to import") },
                    leadingContent    = { Icon(Icons.Default.Sync, contentDescription = null) },
                    trailingContent   = {
                        if (isImportingContacts) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    },
                    modifier = Modifier.clickable(enabled = !isImportingContacts) {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
                            == PackageManager.PERMISSION_GRANTED
                        ) {
                            onNavigateContactPick()
                        } else {
                            contactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                        }
                    },
                )

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

        val cs = viewModel.carouselState
        if (cs is SettingsViewModel.CarouselState.Active) {
            Surface(modifier = Modifier.fillMaxSize()) {
                ContactImportCarousel(
                    state     = cs,
                    onAssign  = { relation -> viewModel.carouselAssignAndNext(relation) },
                    onSkip    = { viewModel.carouselSkip() },
                    onDismiss = { viewModel.dismissCarousel() },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactImportCarousel(
    state: SettingsViewModel.CarouselState.Active,
    onAssign: (String) -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
) {
    val contact  = state.contacts[state.currentIndex]
    val total    = state.contacts.size
    val current  = state.currentIndex + 1
    var relation by remember(state.currentIndex) { mutableStateOf(RelationLabel.ACQUAINTANCE) }
    var expanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import contact $current of $total") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel import")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LinearProgressIndicator(
                progress = { current.toFloat() / total },
                modifier = Modifier.fillMaxWidth(),
            )

            Text(contact.candidate.displayName, style = MaterialTheme.typography.headlineSmall)

            Text("Assign a relationship:", style = MaterialTheme.typography.labelMedium)

            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = relation.toDisplayLabel(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Relationship") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    RelationLabel.ALL.forEach { label ->
                        DropdownMenuItem(
                            text = { Text(label.toDisplayLabel()) },
                            onClick = { relation = label; expanded = false },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = onSkip, modifier = Modifier.weight(1f)) {
                    Text("Skip")
                }
                Button(
                    onClick = { onAssign(relation) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (current == total) "Import" else "Next")
                }
            }
        }
    }
}
