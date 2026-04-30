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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Sync
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.github.maskedkunisquat.wulfpak.core.data.entity.RelationLabel
import com.github.maskedkunisquat.wulfpak.core.logic.llm.ModelLoadState
import com.github.maskedkunisquat.wulfpak.ui.common.toDisplayLabel


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateMerge: () -> Unit,
    onNavigateContactPick: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val biometricEnabled by viewModel.biometricEnabled.collectAsStateWithLifecycle()
    val modelLoadState   by viewModel.modelLoadState.collectAsStateWithLifecycle()

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

    LaunchedEffect(viewModel.downloadError) {
        viewModel.downloadError?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearDownloadError()
        }
    }

    LaunchedEffect(viewModel.carouselMessage) {
        viewModel.carouselMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearCarouselMessage()
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

    Box(modifier = Modifier.fillMaxSize()) {
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
                                checked         = biometricEnabled,
                                onCheckedChange = { viewModel.setBiometricEnabled(it) },
                            )
                        },
                    )
                }

                item { SectionHeader("AI features") }
                item {
                    val modelReady     = modelLoadState == ModelLoadState.READY
                    val modelLoading   = modelLoadState == ModelLoadState.LOADING_SESSION
                    val modelAvailable = viewModel.isModelAvailable
                    val progress       = viewModel.downloadProgress
                    ListItem(
                        headlineContent = {
                            Text(when {
                                modelReady     -> "AI model ready"
                                modelLoading   -> "Loading model…"
                                progress != null -> "Downloading AI model ($progress%)"
                                modelAvailable -> "AI model downloaded"
                                else           -> "Download AI model"
                            })
                        },
                        supportingContent = {
                            Column {
                                Text(when {
                                    modelReady     -> "On-device Gemma 3n E4B is loaded and ready"
                                    modelLoading   -> "Initializing model engine…"
                                    progress != null -> "Download in progress…"
                                    modelAvailable -> "Tap to load into memory"
                                    else           -> "Gemma 3n E4B (~4.9 GB) — required for AI features"
                                })
                                if (progress != null) {
                                    LinearProgressIndicator(
                                        progress = { progress / 100f },
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    )
                                }
                            }
                        },
                        leadingContent  = {
                            if (modelLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            else Icon(Icons.Default.Download, contentDescription = null)
                        },
                        modifier = Modifier.clickable(enabled = !modelReady && !modelLoading && progress == null) {
                            if (modelAvailable) viewModel.loadModel()
                            else viewModel.downloadModel()
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

        // Carousel overlay (full-screen, shown above settings)
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


@Composable
private fun SectionHeader(text: String) {
    Text(
        text     = text,
        style    = MaterialTheme.typography.labelSmall,
        color    = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}
