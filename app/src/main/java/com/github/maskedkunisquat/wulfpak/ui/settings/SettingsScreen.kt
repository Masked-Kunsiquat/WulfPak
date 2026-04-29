package com.github.maskedkunisquat.wulfpak.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Sync
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.maskedkunisquat.wulfpak.core.logic.llm.ModelLoadState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val biometricEnabled by viewModel.biometricEnabled.collectAsStateWithLifecycle()
    val modelLoadState   by viewModel.modelLoadState.collectAsStateWithLifecycle()

    val contactPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.syncContacts() }

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
                            // file exists but not loaded — nothing to do from UI; LlmOrchestrator
                            // calls initialize() on first use
                        } else {
                            viewModel.downloadModel()
                        }
                    },
                )
            }

            item { SectionHeader("Data import") }
            item {
                val isSyncing = viewModel.syncState is SettingsViewModel.SyncState.Loading
                ListItem(
                    headlineContent   = { Text("Import from Contacts") },
                    supportingContent = { Text("Sync device contacts into WulfPak") },
                    leadingContent    = { Icon(Icons.Default.Sync, contentDescription = null) },
                    trailingContent   = {
                        if (isSyncing) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    },
                    modifier = Modifier.clickable(enabled = !isSyncing) {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
                            == PackageManager.PERMISSION_GRANTED
                        ) {
                            viewModel.syncContacts()
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
