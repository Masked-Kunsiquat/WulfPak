package com.github.maskedkunisquat.wulfpak.ui.person

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.maskedkunisquat.wulfpak.core.data.entity.InteractionType
import com.github.maskedkunisquat.wulfpak.ui.common.toDisplayDate
import com.github.maskedkunisquat.wulfpak.ui.common.toDisplayLabel
import java.util.TimeZone
import java.util.UUID

private val ALL_INTERACTION_TYPES = listOf(
    InteractionType.IN_PERSON, InteractionType.CALL, InteractionType.VIDEO_CALL,
    InteractionType.TEXT, InteractionType.EMAIL, InteractionType.SOCIAL_MEDIA,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditInteractionScreen(
    personId: String,
    interactionId: UUID? = null,
    onNavigateBack: () -> Unit,
    viewModel: AddEditInteractionViewModel = viewModel(),
) {
    LaunchedEffect(personId, interactionId) { viewModel.load(personId, interactionId) }

    var typeExpanded       by remember { mutableStateOf(false) }
    var showDatePicker     by remember { mutableStateOf(false) }
    var participantSearch  by remember { mutableStateOf("") }
    val datePickerState    = rememberDatePickerState(initialSelectedDateMillis = viewModel.timestampMs)
    val allPersons         by viewModel.allPersons.collectAsStateWithLifecycle()

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val utc = datePickerState.selectedDateMillis ?: viewModel.timestampMs
                    viewModel.timestampMs = utc - TimeZone.getDefault().getOffset(utc)
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
                title = { Text(if (interactionId == null) "Log Interaction" else "Edit Interaction") },
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
                    value = viewModel.type.toDisplayLabel(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(
                    expanded = typeExpanded,
                    onDismissRequest = { typeExpanded = false },
                ) {
                    ALL_INTERACTION_TYPES.forEach { t ->
                        DropdownMenuItem(
                            text = { Text(t.toDisplayLabel()) },
                            onClick = { viewModel.type = t; typeExpanded = false },
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Date: ${viewModel.timestampMs.toDisplayDate()}")
            }

            OutlinedTextField(
                value = viewModel.durationMins,
                onValueChange = { viewModel.durationMins = it },
                label = { Text("Duration (minutes)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )

            OutlinedTextField(
                value = viewModel.note,
                onValueChange = { viewModel.note = it },
                label = { Text("Note") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
            )

            if (allPersons.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    text     = "Who was there?",
                    style    = MaterialTheme.typography.labelMedium,
                    color    = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
                OutlinedTextField(
                    value         = participantSearch,
                    onValueChange = { participantSearch = it },
                    label         = { Text("Search") },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true,
                )
                val filtered = allPersons.filter { p ->
                    participantSearch.isBlank() ||
                    "${p.firstName} ${p.lastName ?: ""}".contains(participantSearch, ignoreCase = true)
                }
                filtered.forEach { person ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.togglePerson(person.id) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked         = person.id in viewModel.selectedIds,
                            onCheckedChange = { viewModel.togglePerson(person.id) },
                        )
                        Text(
                            text     = "${person.firstName}${person.lastName?.let { " $it" } ?: ""}",
                            modifier = Modifier.padding(start = 4.dp),
                            style    = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}
