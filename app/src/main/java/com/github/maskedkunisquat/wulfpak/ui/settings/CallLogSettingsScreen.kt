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
import androidx.compose.material3.SelectableDates
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallLogSettingsScreen(viewModel: SettingsViewModel, onNavigateBack: () -> Unit) {
    val importSince by viewModel.callLogImportSince.collectAsStateWithLifecycle()
    val noFutureDates = remember {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val nowCal  = Calendar.getInstance()
                val dateCal = Calendar.getInstance().apply { timeInMillis = utcTimeMillis }
                return dateCal.get(Calendar.YEAR) < nowCal.get(Calendar.YEAR) ||
                    (dateCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
                     dateCal.get(Calendar.DAY_OF_YEAR) <= nowCal.get(Calendar.DAY_OF_YEAR))
            }
        }
    }
    val datePickerState = rememberDatePickerState(selectableDates = noFutureDates)
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(importSince) {
        if (!initialized) {
            if (importSince > 0L) datePickerState.selectedDateMillis = importSince
            initialized = true
        }
    }

    LaunchedEffect(datePickerState.selectedDateMillis, initialized) {
        if (!initialized) return@LaunchedEffect
        viewModel.setCallLogImportSince(datePickerState.selectedDateMillis ?: 0L)
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
