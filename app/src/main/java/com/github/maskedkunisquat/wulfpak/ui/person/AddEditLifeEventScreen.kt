package com.github.maskedkunisquat.wulfpak.ui.person

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.maskedkunisquat.wulfpak.core.data.entity.LifeEventType
import com.github.maskedkunisquat.wulfpak.ui.common.toDisplayDate
import com.github.maskedkunisquat.wulfpak.ui.common.toDisplayLabel
import java.util.UUID

private val ALL_LIFE_EVENT_TYPES = listOf(
    LifeEventType.BIRTHDAY, LifeEventType.ANNIVERSARY, LifeEventType.GRADUATION,
    LifeEventType.JOB_CHANGE, LifeEventType.MOVED, LifeEventType.DEATH,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditLifeEventScreen(
    personId: String,
    lifeEventId: UUID? = null,
    onNavigateBack: () -> Unit,
    viewModel: AddEditLifeEventViewModel = viewModel(),
) {
    LaunchedEffect(personId, lifeEventId) { viewModel.load(personId, lifeEventId) }

    var typeExpanded   by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = viewModel.dateMs)

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dateMs = datePickerState.selectedDateMillis ?: viewModel.dateMs
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) { DatePicker(state = datePickerState) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (lifeEventId == null) "Add Life Event" else "Edit Life Event") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.save(onNavigateBack) }) { Text("Save") }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ExposedDropdownMenuBox(
                expanded = typeExpanded,
                onExpandedChange = { typeExpanded = it },
            ) {
                OutlinedTextField(
                    value = viewModel.eventType.toDisplayLabel(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Event type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(
                    expanded = typeExpanded,
                    onDismissRequest = { typeExpanded = false },
                ) {
                    ALL_LIFE_EVENT_TYPES.forEach { t ->
                        DropdownMenuItem(
                            text = { Text(t.toDisplayLabel()) },
                            onClick = { viewModel.eventType = t; typeExpanded = false },
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Date: ${viewModel.dateMs.toDisplayDate()}")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Recurring yearly", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = viewModel.isRecurring,
                    onCheckedChange = { viewModel.isRecurring = it },
                )
            }

            OutlinedTextField(
                value = viewModel.note,
                onValueChange = { viewModel.note = it },
                label = { Text("Note") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 5,
            )
        }
    }
}
