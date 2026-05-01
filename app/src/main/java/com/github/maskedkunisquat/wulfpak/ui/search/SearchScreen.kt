package com.github.maskedkunisquat.wulfpak.ui.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.maskedkunisquat.wulfpak.core.logic.llm.ModelLoadState
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onOpenSettings: () -> Unit,
    viewModel: SearchViewModel = viewModel(),
) {
    val modelState   by viewModel.modelLoadState.collectAsStateWithLifecycle()
    val modelReady   = modelState == ModelLoadState.READY
    val modelLoading = modelState == ModelLoadState.LOADING_SESSION
    val listState    = rememberLazyListState()

    LaunchedEffect(viewModel.messages.size) {
        if (viewModel.messages.isNotEmpty()) listState.animateScrollToItem(viewModel.messages.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ask AI") },
                actions = {
                    if (viewModel.messages.isNotEmpty()) {
                        IconButton(onClick = viewModel::clearConversation) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear conversation")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().imePadding()) {

            // Messages
            LazyColumn(
                state    = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                if (viewModel.messages.isEmpty()) {
                    item {
                        LaunchedEffect(Unit) {
                            while (true) { delay(8_000); viewModel.rotateSuggestions() }
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 24.dp, start = 16.dp, end = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (!modelReady) {
                                ModelWarningBanner(modelLoading, onOpenSettings)
                            } else {
                                AnimatedContent(targetState = viewModel.suggestions, label = "chips") { chips ->
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        chips.forEach { prompt ->
                                            SuggestionChip(
                                                onClick = { viewModel.query = prompt; viewModel.askAi() },
                                                label   = { Text(prompt, maxLines = 1) },
                                            )
                                        }
                                    }
                                }
                                Text(
                                    "Ask anything about your contacts, notes, and memories",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                } else {
                    itemsIndexed(viewModel.messages, key = { idx, _ -> idx }) { idx, msg ->
                        when (msg) {
                            is ChatMessage.ToolCall     -> ToolCallBubble(
                                message  = msg,
                                onToggle = { viewModel.toggleToolCall(idx) },
                            )
                            is ChatMessage.PendingWrite -> PendingWriteBubble(
                                message   = msg,
                                onConfirm = { viewModel.confirmPendingWrite(msg.id) },
                                onCancel  = { viewModel.cancelPendingWrite(msg.id) },
                            )
                            else -> ChatBubble(msg)
                        }
                    }
                    if (!modelReady) {
                        item { ModelWarningBanner(modelLoading, onOpenSettings, compact = true) }
                    }
                }
            }

            // Input
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value         = viewModel.query,
                    onValueChange = { viewModel.query = it },
                    placeholder   = { Text("Ask about your contacts…") },
                    singleLine    = true,
                    modifier      = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { viewModel.askAi() }),
                    trailingIcon  = {
                        if (viewModel.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.query = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                )
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = viewModel::askAi,
                    enabled = !viewModel.isQuerying && viewModel.query.isNotBlank(),
                ) {
                    if (viewModel.isQuerying) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    when (message) {
        is ChatMessage.ToolCall     -> Unit
        is ChatMessage.PendingWrite -> Unit
        is ChatMessage.User -> Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.padding(start = 56.dp),
            ) {
                Text(
                    message.text,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        is ChatMessage.Assistant -> Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.padding(end = 56.dp),
            ) {
                if (message.isStreaming && message.text.isEmpty()) {
                    LinearProgressIndicator(
                        modifier = Modifier.width(80.dp).padding(horizontal = 12.dp, vertical = 18.dp),
                    )
                } else {
                    Text(
                        message.text,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style    = MaterialTheme.typography.bodyMedium,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolCallBubble(message: ChatMessage.ToolCall, onToggle: () -> Unit) {
    val label = when (message.name) {
        "getContactDetails"    -> message.args["name"]?.let { "looked up details for $it" } ?: "looked up contact details"
        "getContactNotes"      -> message.args["name"]?.takeIf { it.isNotBlank() }?.let { "looked up notes for $it" } ?: "looked up recent notes"
        "getContactGifts"      -> message.args["name"]?.takeIf { it.isNotBlank() }?.let { "looked up gifts for $it" } ?: "looked up all gift ideas"
        "getContactHistory"    -> message.args["name"]?.takeIf { it.isNotBlank() }?.let { "looked up history for $it" } ?: "looked up recent activity"
        "getPendingTasks"      -> message.args["name"]?.takeIf { it.isNotBlank() }?.let { "looked up tasks for $it" } ?: "looked up pending tasks"
        "getUpcomingEvents"    -> "looked up upcoming events"
        "searchAcrossContacts" -> message.args["query"]?.let { "searched for \"$it\"" } ?: "searched notes & memories"
        "getLapsedContacts"    -> "checked lapsed contacts"
        "findContactsByRelation" -> message.args["relation"]?.let { "filtered by \"$it\"" } ?: "filtered by relation"
        "getLifeEvents"        -> message.args["name"]?.let { "looked up life events for $it" } ?: "looked up life events"
        "getRelationshipWeb"   -> message.args["name"]?.let { "looked up connections for $it" } ?: "looked up connections"
        "logInteraction"       -> message.args["name"]?.let { "logging interaction with $it" } ?: "logging interaction"
        "addNote"              -> message.args["name"]?.let { "adding note for $it" } ?: "adding note"
        "addTask"              -> message.args["name"]?.let { "adding task for $it" } ?: "adding task"
        "addGiftIdea"          -> message.args["name"]?.let { "adding gift idea for $it" } ?: "adding gift idea"
        else                   -> message.name
    }
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            onClick  = onToggle,
            shape    = MaterialTheme.shapes.small,
            color    = MaterialTheme.colorScheme.tertiaryContainer,
            modifier = Modifier.wrapContentWidth(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Default.Build,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint     = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Icon(
                        if (message.isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (message.isExpanded) "Collapse" else "Expand",
                        modifier = Modifier.size(12.dp),
                        tint     = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
                AnimatedVisibility(visible = message.isExpanded) {
                    val detail = buildString {
                        appendLine("fn: ${message.name}")
                        message.args.forEach { (k, v) -> appendLine("$k: $v") }
                    }.trimEnd()
                    Text(
                        detail,
                        modifier   = Modifier.padding(top = 4.dp),
                        style      = MaterialTheme.typography.labelSmall,
                        color      = MaterialTheme.colorScheme.onTertiaryContainer,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingWriteBubble(
    message: ChatMessage.PendingWrite,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (message.state) {
            WriteState.PENDING -> Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            message.description,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onConfirm, modifier = Modifier.weight(1f)) { Text("Confirm") }
                        OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    }
                }
            }
            WriteState.CONFIRMED -> Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        "${message.description} — saved",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            WriteState.CANCELLED -> Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Cancelled",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelWarningBanner(
    isLoading: Boolean,
    onOpenSettings: () -> Unit,
    compact: Boolean = false,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = if (compact) 4.dp else 12.dp),
        onClick = onOpenSettings,
        colors  = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier    = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color       = MaterialTheme.colorScheme.onErrorContainer,
                )
            } else {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint     = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Text(
                if (isLoading) "AI model is loading…"
                else "AI model not loaded — tap to open Settings",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}
