package com.github.maskedkunisquat.wulfpak.ui.person

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.maskedkunisquat.wulfpak.core.data.entity.Gift
import com.github.maskedkunisquat.wulfpak.core.data.entity.GiftStatus
import com.github.maskedkunisquat.wulfpak.core.data.entity.Interaction
import com.github.maskedkunisquat.wulfpak.core.data.entity.LifeEvent
import com.github.maskedkunisquat.wulfpak.core.data.entity.Note
import com.github.maskedkunisquat.wulfpak.core.data.entity.Task
import com.github.maskedkunisquat.wulfpak.ui.common.PersonAvatar
import com.github.maskedkunisquat.wulfpak.ui.common.toDisplayDate
import com.github.maskedkunisquat.wulfpak.ui.common.toDisplayLabel
import java.util.UUID

private val TABS = listOf("Interactions", "Notes", "Life Events", "Gifts", "Tasks", "Summarize")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonDetailScreen(
    viewModel: PersonDetailViewModel,
    onNavigateBack: () -> Unit,
    onEdit: () -> Unit,
    onAddInteraction: () -> Unit,
    onAddNote: () -> Unit,
    onAddLifeEvent: () -> Unit,
    onAddGift: () -> Unit,
    onAddTask: () -> Unit,
    onEditInteraction: (UUID) -> Unit,
    onEditNote: (UUID) -> Unit,
    onEditLifeEvent: (UUID) -> Unit,
    onEditGift: (UUID) -> Unit,
    onEditTask: (UUID) -> Unit,
) {
    val person       by viewModel.person.collectAsStateWithLifecycle()
    val interactions by viewModel.interactions.collectAsStateWithLifecycle()
    val notes        by viewModel.notes.collectAsStateWithLifecycle()
    val lifeEvents   by viewModel.lifeEvents.collectAsStateWithLifecycle()
    val gifts        by viewModel.gifts.collectAsStateWithLifecycle()
    val tasks        by viewModel.tasks.collectAsStateWithLifecycle()

    var selectedTab     by remember { mutableIntStateOf(0) }
    var showOverflow    by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val fabAction: (() -> Unit)? = when (selectedTab) {
        0 -> onAddInteraction
        1 -> onAddNote
        2 -> onAddLifeEvent
        3 -> onAddGift
        4 -> onAddTask
        else -> null  // Summarize tab — no FAB
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete ${person?.firstName}?") },
            text = { Text("This will permanently delete this person and all associated records.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deletePerson(onNavigateBack) }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(person?.let { "${it.firstName}${it.lastName?.let { n -> " $n" } ?: ""}" } ?: "") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    Box {
                        IconButton(onClick = { showOverflow = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                            DropdownMenuItem(
                                text = { Text("Delete person") },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                onClick = { showOverflow = false; showDeleteConfirm = true },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            fabAction?.let { action ->
                FloatingActionButton(onClick = action) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            person?.let { p ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PersonAvatar(p, size = 56.dp)
                    Column {
                        Text(p.relationLabel.toDisplayLabel(), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary)
                        p.nickname?.let { Text("\"$it\"", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }

            PrimaryTabRow(selectedTabIndex = selectedTab) {
                TABS.forEachIndexed { i, title ->
                    Tab(selected = selectedTab == i, onClick = { selectedTab = i },
                        text = { Text(title) })
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedTab) {
                    0 -> InteractionsTab(interactions,
                        onEdit = onEditInteraction,
                        onDelete = viewModel::deleteInteraction)
                    1 -> NotesTab(notes,
                        onEdit = onEditNote,
                        onDelete = viewModel::deleteNote)
                    2 -> LifeEventsTab(lifeEvents,
                        onEdit = onEditLifeEvent,
                        onDelete = viewModel::deleteLifeEvent)
                    3 -> GiftsTab(gifts,
                        onEdit = onEditGift,
                        onDelete = viewModel::deleteGift)
                    4 -> TasksTab(tasks,
                        onToggleDone = viewModel::toggleTaskDone,
                        onEdit = onEditTask,
                        onDelete = viewModel::deleteTask)
                    5 -> SummarizeTab(
                        text = viewModel.summarizeText,
                        isLoading = viewModel.isSummarizing,
                        onSummarize = viewModel::summarize,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyTab(message: String) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InteractionsTab(
    items: List<Interaction>,
    onEdit: (UUID) -> Unit,
    onDelete: (Interaction) -> Unit,
) {
    if (items.isEmpty()) { EmptyTab("No interactions yet — tap + to log one"); return }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items, key = { it.id }) { interaction ->
            ListItem(
                modifier = Modifier.clickable { onEdit(interaction.id) },
                headlineContent = { Text(interaction.type.toDisplayLabel()) },
                supportingContent = {
                    interaction.note?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                },
                overlineContent = { Text(interaction.timestamp.toDisplayDate()) },
                trailingContent = {
                    IconButton(onClick = { onDelete(interaction) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error)
                    }
                },
            )
        }
    }
}

@Composable
private fun NotesTab(items: List<Note>, onEdit: (UUID) -> Unit, onDelete: (Note) -> Unit) {
    if (items.isEmpty()) { EmptyTab("No notes yet — tap + to add one"); return }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items, key = { it.id }) { note ->
            ListItem(
                modifier = Modifier.clickable { onEdit(note.id) },
                headlineContent = { Text(note.body, maxLines = 3, overflow = TextOverflow.Ellipsis) },
                overlineContent = { Text(note.timestamp.toDisplayDate()) },
                trailingContent = {
                    IconButton(onClick = { onDelete(note) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error)
                    }
                },
            )
        }
    }
}

@Composable
private fun LifeEventsTab(
    items: List<LifeEvent>,
    onEdit: (UUID) -> Unit,
    onDelete: (LifeEvent) -> Unit,
) {
    if (items.isEmpty()) { EmptyTab("No life events — tap + to add one"); return }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items, key = { it.id }) { event ->
            ListItem(
                modifier = Modifier.clickable { onEdit(event.id) },
                headlineContent = { Text(event.eventType.toDisplayLabel()) },
                supportingContent = {
                    val recurring = if (event.isRecurring) " · recurring" else ""
                    Text(event.date.toDisplayDate() + recurring)
                },
                trailingContent = {
                    IconButton(onClick = { onDelete(event) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error)
                    }
                },
            )
        }
    }
}

@Composable
private fun GiftsTab(items: List<Gift>, onEdit: (UUID) -> Unit, onDelete: (Gift) -> Unit) {
    if (items.isEmpty()) { EmptyTab("No gifts tracked — tap + to add one"); return }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items, key = { it.id }) { gift ->
            ListItem(
                modifier = Modifier.clickable { onEdit(gift.id) },
                headlineContent = { Text(gift.name) },
                supportingContent = {
                    val occasion = gift.occasion?.let { " · $it" } ?: ""
                    Text(gift.status + occasion)
                },
                trailingContent = {
                    IconButton(onClick = { onDelete(gift) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error)
                    }
                },
            )
        }
    }
}

@Composable
private fun TasksTab(
    items: List<Task>,
    onToggleDone: (Task) -> Unit,
    onEdit: (UUID) -> Unit,
    onDelete: (Task) -> Unit,
) {
    if (items.isEmpty()) { EmptyTab("No tasks — tap + to add one"); return }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items, key = { it.id }) { task ->
            ListItem(
                modifier = Modifier.clickable { onEdit(task.id) },
                headlineContent = {
                    Text(
                        text = task.title,
                        textDecoration = if (task.isDone) TextDecoration.LineThrough else null,
                    )
                },
                supportingContent = task.dueAt?.let { { Text("Due ${it.toDisplayDate()}") } },
                leadingContent = {
                    IconButton(onClick = { onToggleDone(task) }) {
                        Icon(
                            imageVector = if (task.isDone) Icons.Default.CheckBox
                                          else Icons.Default.CheckBoxOutlineBlank,
                            contentDescription = if (task.isDone) "Mark incomplete" else "Mark done",
                            tint = if (task.isDone) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                trailingContent = {
                    IconButton(onClick = { onDelete(task) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error)
                    }
                },
            )
        }
    }
}

@Composable
private fun SummarizeTab(text: String, isLoading: Boolean, onSummarize: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            text.isEmpty() && !isLoading -> {
                Button(
                    onClick = onSummarize,
                    modifier = Modifier.align(Alignment.Center),
                ) { Text("Generate Summary") }
            }
            text.isEmpty() -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        item { Text(text, style = MaterialTheme.typography.bodyMedium) }
                    }
                    if (!isLoading) {
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = onSummarize) { Text("Regenerate") }
                        }
                    }
                }
            }
        }
    }
}
