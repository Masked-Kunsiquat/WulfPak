package com.github.maskedkunisquat.wulfpak.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkManager
import com.github.maskedkunisquat.wulfpak.AppApplication
import com.github.maskedkunisquat.wulfpak.AppPrefsKeys
import com.github.maskedkunisquat.wulfpak.appDataStore
import com.github.maskedkunisquat.wulfpak.worker.CallLogImportWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.github.maskedkunisquat.wulfpak.ui.common.toDisplayDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateSecurity: () -> Unit,
    onNavigateDisplay: () -> Unit,
    onNavigateAi: () -> Unit,
    onNavigateContacts: () -> Unit,
    onNavigateDebugSummary: () -> Unit,
    onNavigateCallLogSettings: () -> Unit,
    onSwitchProfile: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as? AppApplication
    val scope = rememberCoroutineScope()
    val isDemoProfile = app?.isDemoProfile ?: false
    var showProfileDialog by remember { mutableStateOf(false) }

    val captureEnabled by context.appDataStore.data
        .map { it[AppPrefsKeys.DEBUG_CAPTURE_ENABLED] ?: false }
        .collectAsStateWithLifecycle(false)

    val callLogEnabled by context.appDataStore.data
        .map { it[AppPrefsKeys.CALL_LOG_IMPORT_ENABLED] ?: false }
        .collectAsStateWithLifecycle(false)

    val callLogImportSince by context.appDataStore.data
        .map { it[AppPrefsKeys.CALL_LOG_IMPORT_SINCE] ?: 0L }
        .collectAsStateWithLifecycle(0L)

    val snackbarHostState = remember { SnackbarHostState() }

    val callLogPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scope.launch {
                context.appDataStore.edit { it[AppPrefsKeys.CALL_LOG_IMPORT_ENABLED] = true }
            }
            CallLogImportWorker.schedule(WorkManager.getInstance(context))
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Call log permission required for auto-import")
            }
        }
    }

    var eventCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(captureEnabled) {
        eventCount = withContext(Dispatchers.IO) { app?.debugEventLogger?.totalCount() ?: 0 }
    }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(3_000)
            eventCount = withContext(Dispatchers.IO) { app?.debugEventLogger?.totalCount() ?: 0 }
        }
    }

    if (showProfileDialog) {
        AlertDialog(
            onDismissRequest = { showProfileDialog = false },
            title = { Text(if (isDemoProfile) "Exit demo mode?" else "Try demo mode?") },
            text  = {
                Text(
                    if (isDemoProfile)
                        "The app will restart and return to your real data."
                    else
                        "The app will restart in demo mode. Your real data is safely preserved."
                )
            },
            confirmButton = {
                Button(onClick = { showProfileDialog = false; onSwitchProfile() }) {
                    Text(if (isDemoProfile) "Exit demo" else "Try demo")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showProfileDialog = false }) { Text("Cancel") }
            },
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
        Column(modifier = Modifier.padding(padding)) {
            ListItem(
                headlineContent   = { Text("Security") },
                supportingContent = { Text("Biometric lock") },
                leadingContent    = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingContent   = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                modifier          = Modifier.clickable(onClick = onNavigateSecurity),
            )
            ListItem(
                headlineContent   = { Text("Display") },
                supportingContent = { Text("Birthday ages and appearance") },
                leadingContent    = { Icon(Icons.Default.Tune, contentDescription = null) },
                trailingContent   = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                modifier          = Modifier.clickable(onClick = onNavigateDisplay),
            )
            ListItem(
                headlineContent   = { Text("AI") },
                supportingContent = { Text("On-device model and search index") },
                leadingContent    = { Icon(Icons.Default.Memory, contentDescription = null) },
                trailingContent   = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                modifier          = Modifier.clickable(onClick = onNavigateAi),
            )
            ListItem(
                headlineContent   = { Text("Contacts") },
                supportingContent = { Text("Import, export, and merge") },
                leadingContent    = { Icon(Icons.Default.People, contentDescription = null) },
                trailingContent   = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                modifier          = Modifier.clickable(onClick = onNavigateContacts),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val sinceText = if (callLogImportSince > 0L) {
                    "From ${callLogImportSince.toDisplayDate()}"
                } else {
                    "All history"
                }
                ListItem(
                    headlineContent   = { Text("Auto-import calls") },
                    supportingContent = { Text(sinceText) },
                    leadingContent    = { Icon(Icons.Default.Phone, contentDescription = null) },
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigateCallLogSettings() },
                )
                VerticalDivider(modifier = Modifier.fillMaxHeight().padding(vertical = 12.dp))
                Switch(
                    checked = callLogEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG)
                                == PackageManager.PERMISSION_GRANTED
                            ) {
                                scope.launch {
                                    context.appDataStore.edit { it[AppPrefsKeys.CALL_LOG_IMPORT_ENABLED] = true }
                                }
                                CallLogImportWorker.schedule(WorkManager.getInstance(context))
                            } else {
                                callLogPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
                            }
                        } else {
                            scope.launch {
                                context.appDataStore.edit { it[AppPrefsKeys.CALL_LOG_IMPORT_ENABLED] = false }
                            }
                            WorkManager.getInstance(context).cancelUniqueWork(CallLogImportWorker.WORK_NAME)
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ListItem(
                    headlineContent   = { Text("Debug Capture") },
                    supportingContent = {
                        Text(if (captureEnabled) "$eventCount events captured" else "Off")
                    },
                    leadingContent    = { Icon(Icons.Default.BugReport, contentDescription = null) },
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = captureEnabled && eventCount > 0) {
                            onNavigateDebugSummary()
                        },
                )
                VerticalDivider(modifier = Modifier.fillMaxHeight().padding(vertical = 12.dp))
                Switch(
                    checked = captureEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            context.appDataStore.edit { it[AppPrefsKeys.DEBUG_CAPTURE_ENABLED] = enabled }
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            ListItem(
                headlineContent   = { Text("Profile") },
                supportingContent = { Text("Switch between your data and demo mode") },
                leadingContent    = { Icon(Icons.Default.Science, contentDescription = null) },
            )
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                SegmentedButton(
                    selected = !isDemoProfile,
                    onClick  = { if (isDemoProfile) showProfileDialog = true },
                    shape    = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("Real data") }
                SegmentedButton(
                    selected = isDemoProfile,
                    onClick  = { if (!isDemoProfile) showProfileDialog = true },
                    shape    = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("Demo") }
            }
        }
    }
}
