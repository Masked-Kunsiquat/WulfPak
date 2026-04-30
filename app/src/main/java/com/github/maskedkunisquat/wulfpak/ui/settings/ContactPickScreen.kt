package com.github.maskedkunisquat.wulfpak.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactPickScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
) {
    LaunchedEffect(Unit) { viewModel.startCandidatePick() }

    when (val state = viewModel.candidatePickState) {
        is SettingsViewModel.CandidatePickState.Idle, SettingsViewModel.CandidatePickState.Loading -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Select contacts") },
                        navigationIcon = {
                            IconButton(onClick = {
                                viewModel.dismissCandidatePick()
                                onNavigateBack()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel")
                            }
                        },
                    )
                }
            ) { padding ->
                Box(
                    modifier = Modifier.padding(padding).fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
        is SettingsViewModel.CandidatePickState.Active -> {
            ContactPickerList(
                state = state,
                onToggle = { viewModel.candidatePickToggle(it) },
                onConfirm = {
                    viewModel.candidatePickConfirm()
                    onNavigateBack()
                },
                onDismiss = {
                    viewModel.dismissCandidatePick()
                    onNavigateBack()
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactPickerList(
    state: SettingsViewModel.CandidatePickState.Active,
    onToggle: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }

    val filtered = remember(state.candidates, searchQuery) {
        state.candidates.mapIndexed { i, c -> i to c }.let { all ->
            if (searchQuery.isBlank()) all
            else all.filter { (_, c) -> c.displayName.contains(searchQuery, ignoreCase = true) }
        }
    }

    val newCount = state.selected.count { !state.candidates[it].alreadyImported }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select contacts") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                },
                actions = {
                    TextButton(onClick = onConfirm, enabled = newCount > 0) {
                        Text("Import $newCount")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search ${state.candidates.size} contacts…") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            HorizontalDivider()
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(filtered, key = { (origIdx, _) -> origIdx }) { (originalIndex, candidate) ->
                    val isChecked = originalIndex in state.selected
                    ListItem(
                        headlineContent = { Text(candidate.displayName) },
                        supportingContent = if (candidate.alreadyImported) {
                            { Text("Already in your contacts", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        } else null,
                        leadingContent = {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = null,
                                enabled = !candidate.alreadyImported,
                            )
                        },
                        modifier = if (candidate.alreadyImported) Modifier
                        else Modifier.clickable { onToggle(originalIndex) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
