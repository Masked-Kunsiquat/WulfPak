package com.github.maskedkunisquat.wulfpak.ui.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.maskedkunisquat.wulfpak.core.logic.llm.ModelLoadState
import com.github.maskedkunisquat.wulfpak.core.logic.search.SearchHit
import com.github.maskedkunisquat.wulfpak.ui.common.toDisplayDate
import com.github.maskedkunisquat.wulfpak.ui.common.toDisplayLabel
import kotlinx.coroutines.delay
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onOpenPerson: (UUID) -> Unit,
    onOpenActivity: (UUID) -> Unit,
    onOpenInteraction: (UUID) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: SearchViewModel = viewModel(),
) {
    val isAskAi      = viewModel.isAskAiMode
    val isQuerying   = if (isAskAi) viewModel.isNlQuerying else viewModel.isSearching
    val modelState   by viewModel.modelLoadState.collectAsStateWithLifecycle()
    val modelReady   = modelState == ModelLoadState.READY
    val modelLoading = modelState == ModelLoadState.LOADING_SESSION

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search") },
                actions = {
                    if (isAskAi && viewModel.messages.isNotEmpty()) {
                        IconButton(onClick = viewModel::clearConversation) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear conversation")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().imePadding()) {

            // ── Mode toggle ───────────────────────────────────────────────
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = !isAskAi,
                    onClick  = { viewModel.isAskAiMode = false; viewModel.clearResults() },
                    label    = { Text("Semantic") },
                )
                FilterChip(
                    selected = isAskAi,
                    onClick  = { viewModel.isAskAiMode = true },
                    label    = { Text("Ask AI") },
                )
            }
            HorizontalDivider()

            if (isAskAi) {
                // ── Ask AI — chat layout ──────────────────────────────────
                val listState = rememberLazyListState()
                LaunchedEffect(viewModel.messages.size) {
                    if (viewModel.messages.isNotEmpty()) {
                        listState.animateScrollToItem(viewModel.messages.size - 1)
                    }
                }

                // Chat messages (fills remaining space)
                LazyColumn(
                    state    = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) {
                    if (viewModel.messages.isEmpty()) {
                        item {
                            // Suggestion chips when idle
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
                                        "Ask anything about your contacts and notes",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    } else {
                        itemsIndexed(viewModel.messages, key = { idx, _ -> idx }) { _, msg ->
                            ChatBubble(msg)
                        }
                        // Model warning as last item when not ready and there are messages
                        if (!modelReady) {
                            item { ModelWarningBanner(modelLoading, onOpenSettings, compact = true) }
                        }
                    }
                }

                // Input row pinned to bottom
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
                        onClick  = viewModel::askAi,
                        enabled  = !isQuerying && viewModel.query.isNotBlank(),
                    ) {
                        if (isQuerying) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                        }
                    }
                }

            } else {
                // ── Semantic search layout ────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value         = viewModel.query,
                        onValueChange = { viewModel.query = it },
                        placeholder   = { Text("Search notes, interactions…") },
                        singleLine    = true,
                        modifier      = Modifier.weight(1f),
                        leadingIcon   = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon  = {
                            if (viewModel.query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.query = ""; viewModel.clearResults() }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
                    )
                    IconButton(onClick = viewModel::search, enabled = !isQuerying) {
                        if (isQuerying) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    }
                }

                when {
                    viewModel.results.isEmpty() && !viewModel.isSearching && viewModel.query.isNotBlank() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No results", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    viewModel.results.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "Search your notes and interactions using natural language",
                                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(32.dp),
                            )
                        }
                    }
                    else -> {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(viewModel.results, key = { it.hashCode() }) { hit ->
                                SearchResultItem(hit, onOpenPerson, onOpenActivity, onOpenInteraction)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    when (message) {
        is ChatMessage.User -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
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
        }
        is ChatMessage.Assistant -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(end = 56.dp),
                ) {
                    if (message.isStreaming && message.text.isEmpty()) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .width(80.dp)
                                .padding(horizontal = 12.dp, vertical = 18.dp),
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
            .padding(horizontal = 16.dp, vertical = if (compact) 4.dp else 12.dp)
            .clickable(onClick = onOpenSettings),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            } else {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
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

@Composable
private fun SearchResultItem(
    hit: SearchHit,
    onOpenPerson: (UUID) -> Unit,
    onOpenActivity: (UUID) -> Unit,
    onOpenInteraction: (UUID) -> Unit,
) {
    when (hit) {
        is SearchHit.NoteHit -> {
            val personId = hit.note.personId
            ListItem(
                modifier          = if (personId != null) Modifier.clickable { onOpenPerson(personId) } else Modifier,
                headlineContent   = { Text(hit.note.body, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                overlineContent   = { Text("Note · ${hit.note.timestamp.toDisplayDate()}") },
                supportingContent = { Text("Relevance ${(hit.score * 100).toInt()}%") },
            )
        }
        is SearchHit.InteractionHit -> ListItem(
            modifier          = Modifier.clickable { onOpenInteraction(hit.interaction.id) },
            headlineContent   = { Text(hit.interaction.type.toDisplayLabel()) },
            overlineContent   = { Text("Interaction · ${hit.interaction.timestamp.toDisplayDate()}") },
            supportingContent = {
                hit.interaction.note?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    ?: Text("Relevance ${(hit.score * 100).toInt()}%")
            },
        )
        is SearchHit.ActivityHit -> ListItem(
            modifier          = Modifier.clickable { onOpenActivity(hit.activity.id) },
            headlineContent   = { Text(hit.activity.title) },
            overlineContent   = { Text("Activity · ${hit.activity.timestamp.toDisplayDate()}") },
            supportingContent = {
                hit.activity.body?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    ?: Text("Relevance ${(hit.score * 100).toInt()}%")
            },
        )
    }
}
