package com.github.maskedkunisquat.wulfpak.ui.pendingcalls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.maskedkunisquat.wulfpak.model.PendingCallStub
import com.github.maskedkunisquat.wulfpak.ui.common.toDisplayDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingCallsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSearch: (String) -> Unit,
    viewModel: PendingCallsViewModel = viewModel(),
) {
    val stubs          by viewModel.pendingStubs.collectAsStateWithLifecycle()
    val confirmedStubs =  viewModel.confirmedStubs
    val isEmpty = stubs.isEmpty() && confirmedStubs.isEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pending calls") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (isEmpty) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("No pending calls", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(confirmedStubs, key = { "confirmed:${it.personId}:${it.timestamp}" }) { stub ->
                    ConfirmedCallCard(
                        stub               = stub,
                        onSaveNote         = { text -> viewModel.saveNote(stub, text) },
                        onDismiss          = { viewModel.dismissConfirmed(stub) },
                        onNavigateToSearch = onNavigateToSearch,
                    )
                }
                items(stubs, key = { "${it.personId}:${it.timestamp}" }) { stub ->
                    PendingCallCard(
                        stub      = stub,
                        onConfirm = { viewModel.confirm(stub) },
                        onSkip    = { viewModel.skip(stub) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfirmedCallCard(
    stub: PendingCallStub,
    onSaveNote: (String) -> Unit,
    onDismiss: () -> Unit,
    onNavigateToSearch: (String) -> Unit,
) {
    var noteText by remember { mutableStateOf("") }
    val seed = "I just confirmed a ${stub.callType.lowercase()} with ${stub.personFirstName} from ${stub.timestamp.toDisplayDate()}. Want to add a note?"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stub.personFirstName, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CallTypeChip(stub.callType)
                Text(formatDuration(stub.durationSeconds), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stub.timestamp.toDisplayDate(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value          = noteText,
                onValueChange  = { noteText = it },
                label          = { Text("Add a note…") },
                modifier       = Modifier.fillMaxWidth(),
                minLines       = 2,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(onClick = { onSaveNote(noteText) }) { Text("Save note") }
                OutlinedButton(onClick = onDismiss) { Text("Dismiss") }
                AssistChip(
                    onClick = { onNavigateToSearch(seed) },
                    label   = { Text("Ask assistant") },
                )
            }
        }
    }
}

@Composable
private fun PendingCallCard(
    stub: PendingCallStub,
    onConfirm: () -> Unit,
    onSkip: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stub.personFirstName, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CallTypeChip(stub.callType)
                Text(formatDuration(stub.durationSeconds), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stub.timestamp.toDisplayDate(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onConfirm) { Text("Confirm") }
                OutlinedButton(onClick = onSkip) { Text("Skip") }
            }
        }
    }
}

@Composable
private fun CallTypeChip(callType: String) {
    val label = when (callType) {
        "INCOMING" -> "Incoming"
        "OUTGOING" -> "Outgoing"
        "MISSED"   -> "Missed"
        else       -> callType
    }
    val color = when (callType) {
        "MISSED" -> MaterialTheme.colorScheme.errorContainer
        else     -> MaterialTheme.colorScheme.secondaryContainer
    }
    val textColor = when (callType) {
        "MISSED" -> MaterialTheme.colorScheme.onErrorContainer
        else     -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(color = color, shape = MaterialTheme.shapes.small) {
        Text(
            text     = label,
            color    = textColor,
            style    = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

private fun formatDuration(seconds: Int?): String {
    if (seconds == null) return "—"
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}
