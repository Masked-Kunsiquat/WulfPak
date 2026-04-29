package com.github.maskedkunisquat.wulfpak.ui.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.maskedkunisquat.wulfpak.ui.common.toDisplayDate
import com.github.maskedkunisquat.wulfpak.ui.common.toDisplayLabel
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityFeedScreen(
    onAddActivity: () -> Unit,
    onEditActivity: (UUID) -> Unit,
    viewModel: ActivityFeedViewModel = viewModel(),
) {
    val feed by viewModel.feed.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Feed") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddActivity) {
                Icon(Icons.Default.Add, contentDescription = "Log activity")
            }
        },
    ) { padding ->
        if (feed.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("No activity yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
                items(feed, key = { when (it) {
                    is FeedItem.InteractionItem -> "i:${it.interaction.id}"
                    is FeedItem.ActivityItem    -> "a:${it.activity.id}"
                }}) { item ->
                    when (item) {
                        is FeedItem.InteractionItem -> {
                            val i = item.interaction
                            ListItem(
                                headlineContent   = { Text(i.type.toDisplayLabel()) },
                                supportingContent = {
                                    i.note?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                                },
                                overlineContent   = { Text(i.timestamp.toDisplayDate()) },
                            )
                        }
                        is FeedItem.ActivityItem -> {
                            val a = item.activity
                            ListItem(
                                modifier          = Modifier.clickable { onEditActivity(a.id) },
                                headlineContent   = { Text(a.title) },
                                supportingContent = {
                                    a.body?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                                },
                                overlineContent   = { Text(a.timestamp.toDisplayDate()) },
                            )
                        }
                    }
                }
            }
        }
    }
}
