package com.github.maskedkunisquat.wulfpak.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.maskedkunisquat.wulfpak.core.logic.search.SearchHit
import com.github.maskedkunisquat.wulfpak.ui.common.toDisplayDate
import com.github.maskedkunisquat.wulfpak.ui.common.toDisplayLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(viewModel: SearchViewModel = viewModel()) {
    val isAskAi   = viewModel.isAskAiMode
    val isQuerying = if (isAskAi) viewModel.isNlQuerying else viewModel.isSearching
    val onSubmit: () -> Unit = if (isAskAi) viewModel::askAi else viewModel::search

    Scaffold(
        topBar = { TopAppBar(title = { Text("Search") }) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = viewModel.query,
                    onValueChange = { viewModel.query = it },
                    placeholder = {
                        Text(if (isAskAi) "Ask anything about your contacts…"
                             else "Search notes, interactions…")
                    },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .onKeyEvent { event ->
                            if (event.key == Key.Enter) { onSubmit(); true } else false
                        },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (viewModel.query.isNotEmpty()) {
                            IconButton(onClick = viewModel::clearResults) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                )
                IconButton(onClick = onSubmit, enabled = !isQuerying) {
                    if (isQuerying) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                }
            }

            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = !isAskAi,
                    onClick = { viewModel.isAskAiMode = false; viewModel.clearResults() },
                    label = { Text("Semantic") },
                )
                FilterChip(
                    selected = isAskAi,
                    onClick = { viewModel.isAskAiMode = true; viewModel.clearResults() },
                    label = { Text("Ask AI") },
                )
            }

            HorizontalDivider()

            if (isAskAi) {
                when {
                    viewModel.nlResponse.isEmpty() && !viewModel.isNlQuerying -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "Ask anything about your contacts and notes",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(32.dp),
                            )
                        }
                    }
                    viewModel.nlResponse.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            item {
                                Text(viewModel.nlResponse, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            } else {
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(32.dp),
                            )
                        }
                    }
                    else -> {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(viewModel.results, key = { it.hashCode() }) { hit ->
                                SearchResultItem(hit)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultItem(hit: SearchHit) {
    when (hit) {
        is SearchHit.NoteHit -> ListItem(
            headlineContent   = {
                Text(hit.note.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
            },
            overlineContent   = { Text("Note · ${hit.note.timestamp.toDisplayDate()}") },
            supportingContent = { Text("Relevance ${(hit.score * 100).toInt()}%") },
        )
        is SearchHit.InteractionHit -> ListItem(
            headlineContent   = { Text(hit.interaction.type.toDisplayLabel()) },
            overlineContent   = { Text("Interaction · ${hit.interaction.timestamp.toDisplayDate()}") },
            supportingContent = {
                hit.interaction.note?.let {
                    Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis)
                } ?: Text("Relevance ${(hit.score * 100).toInt()}%")
            },
        )
        is SearchHit.ActivityHit -> ListItem(
            headlineContent   = { Text(hit.activity.title) },
            overlineContent   = { Text("Activity · ${hit.activity.timestamp.toDisplayDate()}") },
            supportingContent = {
                hit.activity.body?.let {
                    Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis)
                } ?: Text("Relevance ${(hit.score * 100).toInt()}%")
            },
        )
    }
}
