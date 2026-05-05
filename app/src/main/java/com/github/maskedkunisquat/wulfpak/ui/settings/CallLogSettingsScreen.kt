package com.github.maskedkunisquat.wulfpak.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import com.github.maskedkunisquat.wulfpak.AppPrefsKeys
import com.github.maskedkunisquat.wulfpak.appDataStore
import com.github.maskedkunisquat.wulfpak.model.toPendingCallStubs
import com.github.maskedkunisquat.wulfpak.model.toJsonString
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallLogSettingsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val datePickerState = rememberDatePickerState()
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val since = context.appDataStore.data.first()[AppPrefsKeys.CALL_LOG_IMPORT_SINCE] ?: 0L
        if (since > 0L) datePickerState.selectedDateMillis = since
        initialized = true
    }

    LaunchedEffect(datePickerState.selectedDateMillis, initialized) {
        if (!initialized) return@LaunchedEffect
        val newSince = datePickerState.selectedDateMillis ?: 0L
        context.appDataStore.edit { prefs ->
            prefs[AppPrefsKeys.CALL_LOG_IMPORT_SINCE] = newSince
            if (newSince > 0L) {
                val filtered = (prefs[AppPrefsKeys.PENDING_CALL_STUBS] ?: "")
                    .toPendingCallStubs()
                    .filter { it.timestamp >= newSince }
                prefs[AppPrefsKeys.PENDING_CALL_STUBS] = filtered.toJsonString()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Call import cutoff") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Text(
                text = "Only import calls on or after this date.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            DatePicker(
                state = datePickerState,
                modifier = Modifier.fillMaxWidth(),
            )
            if (datePickerState.selectedDateMillis != null) {
                OutlinedButton(
                    onClick = { datePickerState.selectedDateMillis = null },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Text("Clear — import all history")
                }
            }
        }
    }
}
