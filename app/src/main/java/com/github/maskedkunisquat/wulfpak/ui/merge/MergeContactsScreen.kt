package com.github.maskedkunisquat.wulfpak.ui.merge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.maskedkunisquat.wulfpak.core.data.entity.Person
import com.github.maskedkunisquat.wulfpak.ui.common.toDisplayLabel

private data class DialogState(
    val pair: MergeContactsViewModel.DuplicatePair,
    val keepFirst: Boolean = true,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergeContactsScreen(
    viewModel: MergeContactsViewModel = viewModel(),
    onNavigateBack: () -> Unit,
) {
    var dialog by remember { mutableStateOf<DialogState?>(null) }

    dialog?.let { state ->
        val keep    = if (state.keepFirst) state.pair.keep else state.pair.discard
        val discard = if (state.keepFirst) state.pair.discard else state.pair.keep
        AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Merge contacts") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Keep:", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Text(keep.fullName(), style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${keep.relationLabel.toDisplayLabel()} · ${keep.interactionCount} interactions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = { dialog = state.copy(keepFirst = !state.keepFirst) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("↕  Keep ${discard.firstName} instead") }
                    Text(
                        "Discard: ${discard.fullName()}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "All records from ${discard.firstName} will be transferred to " +
                        "${keep.firstName}, then ${discard.firstName} will be removed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.merge(keepId = keep.id, discardId = discard.id)
                        dialog = null
                    },
                    enabled = !viewModel.isMerging,
                ) { Text("Merge") }
            },
            dismissButton = {
                TextButton(onClick = { dialog = null }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Resolve duplicates") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when {
            viewModel.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            viewModel.pairs.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No duplicate contacts found",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(viewModel.pairs, key = { "${it.keep.id}_${it.discard.id}" }) { pair ->
                    ListItem(
                        headlineContent   = { Text(pair.keep.fullName()) },
                        supportingContent = {
                            Text(
                                "${pair.keep.relationLabel.toDisplayLabel()} (${pair.keep.interactionCount})" +
                                " vs ${pair.discard.relationLabel.toDisplayLabel()} (${pair.discard.interactionCount})"
                            )
                        },
                        trailingContent = {
                            TextButton(
                                onClick = { dialog = DialogState(pair) },
                                enabled = !viewModel.isMerging,
                            ) { Text("Merge") }
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

private fun Person.fullName() = buildString {
    append(firstName)
    lastName?.let { append(" $it") }
}
