package com.github.maskedkunisquat.wulfpak.ui.person

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.maskedkunisquat.wulfpak.core.data.entity.Activity
import com.github.maskedkunisquat.wulfpak.core.data.entity.ContactDetail
import com.github.maskedkunisquat.wulfpak.core.data.entity.ContactDetailType
import com.github.maskedkunisquat.wulfpak.core.data.entity.Gift
import com.github.maskedkunisquat.wulfpak.core.data.entity.Interaction
import com.github.maskedkunisquat.wulfpak.core.data.entity.LifeEvent
import com.github.maskedkunisquat.wulfpak.core.data.entity.Note
import com.github.maskedkunisquat.wulfpak.core.data.entity.Task
import com.github.maskedkunisquat.wulfpak.ui.common.PersonAvatar
import com.github.maskedkunisquat.wulfpak.ui.common.toDisplayDate
import com.github.maskedkunisquat.wulfpak.ui.common.toDisplayLabel
import java.util.UUID

private val TABS = listOf("Interactions", "Activities", "Notes", "Life Events", "Gifts", "Tasks")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonDetailScreen(
    viewModel: PersonDetailViewModel,
    onNavigateBack: () -> Unit,
    onEdit: () -> Unit,
    onAddInteraction: () -> Unit,
    onAddActivity: () -> Unit,
    onAddNote: () -> Unit,
    onAddLifeEvent: () -> Unit,
    onAddGift: () -> Unit,
    onAddTask: () -> Unit,
    onEditInteraction: (UUID) -> Unit,
    onEditActivity: (UUID) -> Unit,
    onEditNote: (UUID) -> Unit,
    onEditLifeEvent: (UUID) -> Unit,
    onEditGift: (UUID) -> Unit,
    onEditTask: (UUID) -> Unit,
) {
    val context        = LocalContext.current
    val person         by viewModel.person.collectAsStateWithLifecycle()
    val interactions   by viewModel.interactions.collectAsStateWithLifecycle()
    val activities     by viewModel.activities.collectAsStateWithLifecycle()
    val notes          by viewModel.notes.collectAsStateWithLifecycle()
    val lifeEvents     by viewModel.lifeEvents.collectAsStateWithLifecycle()
    val gifts          by viewModel.gifts.collectAsStateWithLifecycle()
    val tasks          by viewModel.tasks.collectAsStateWithLifecycle()
    val contactDetails by viewModel.contactDetails.collectAsStateWithLifecycle()

    var selectedTab        by remember { mutableIntStateOf(0) }
    var showOverflow       by remember { mutableStateOf(false) }
    var showDeleteConfirm  by remember { mutableStateOf(false) }
    var contactsExpanded   by remember { mutableStateOf(true) }
    var showAddDetail      by remember { mutableStateOf(false) }
    var editingDetail      by remember { mutableStateOf<ContactDetail?>(null) }

    val fabAction: (() -> Unit)? = when (selectedTab) {
        0 -> onAddInteraction
        1 -> onAddActivity
        2 -> onAddNote
        3 -> onAddLifeEvent
        4 -> onAddGift
        5 -> onAddTask
        else -> null
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

    person?.let { p ->
        if (showAddDetail) {
            ContactDetailDialog(
                existing = null,
                personId = p.id,
                onSave   = { detail -> viewModel.addContactDetail(detail); showAddDetail = false },
                onDismiss = { showAddDetail = false },
            )
        }
        editingDetail?.let { editing ->
            ContactDetailDialog(
                existing  = editing,
                personId  = editing.personId,
                onSave    = { detail -> viewModel.updateContactDetail(detail); editingDetail = null },
                onDismiss = { editingDetail = null },
            )
        }
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
                // Avatar + relation header
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

                // Collapsible contact info section
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Contact Info",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Row {
                        IconButton(onClick = { showAddDetail = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Add, contentDescription = "Add contact info",
                                modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { contactsExpanded = !contactsExpanded }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = if (contactsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (contactsExpanded) "Collapse" else "Expand",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = contactsExpanded) {
                    if (contactDetails.isEmpty()) {
                        Text(
                            "No contact info — tap + to add",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                        )
                    } else {
                        Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, bottom = 4.dp)) {
                            contactDetails.forEach { detail ->
                                ContactDetailRow(
                                    detail    = detail,
                                    onLaunch  = { intent -> context.startActivity(intent) },
                                    onEdit    = { editingDetail = it },
                                    onDelete  = { viewModel.deleteContactDetail(it) },
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

                // AI Summary card
                AiSummaryCard(
                    summarizeText = viewModel.summarizeText,
                    isSummarizing = viewModel.isSummarizing,
                    onSummarize   = viewModel::summarize,
                )
            }

            ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp) {
                TABS.forEachIndexed { i, title ->
                    Tab(selected = selectedTab == i, onClick = { selectedTab = i },
                        text = { Text(title) })
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedTab) {
                    0 -> InteractionsTab(interactions, onEdit = onEditInteraction, onDelete = viewModel::deleteInteraction)
                    1 -> ActivitiesTab(activities, onEdit = onEditActivity, onDelete = viewModel::deleteActivity)
                    2 -> NotesTab(notes, onEdit = onEditNote, onDelete = viewModel::deleteNote)
                    3 -> LifeEventsTab(lifeEvents, onEdit = onEditLifeEvent, onDelete = viewModel::deleteLifeEvent)
                    4 -> GiftsTab(gifts, onEdit = onEditGift, onDelete = viewModel::deleteGift)
                    5 -> TasksTab(tasks, onToggleDone = viewModel::toggleTaskDone,
                        onEdit = onEditTask, onDelete = viewModel::deleteTask)
                }
            }
        }
    }
}

@Composable
private fun AiSummaryCard(
    summarizeText: String,
    isSummarizing: Boolean,
    onSummarize: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Summary", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
                if (!isSummarizing) {
                    IconButton(onClick = onSummarize, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "Regenerate",
                            modifier = Modifier.size(18.dp))
                    }
                }
            }
            when {
                isSummarizing -> LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
                summarizeText.isEmpty() -> Text(
                    "Tap refresh to generate a summary.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                else -> Text(
                    summarizeText,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactDetailDialog(
    existing: ContactDetail?,
    personId: UUID,
    onSave: (ContactDetail) -> Unit,
    onDismiss: () -> Unit,
) {
    var type by remember { mutableStateOf(existing?.type ?: ContactDetailType.PHONE) }
    var label by remember { mutableStateOf(existing?.label ?: "") }
    var value by remember { mutableStateOf(existing?.value ?: "") }
    var typeExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add Contact Info" else "Edit Contact Info") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }) {
                    OutlinedTextField(
                        value = type.toDisplayLabel(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        listOf(ContactDetailType.PHONE, ContactDetailType.EMAIL,
                               ContactDetailType.SOCIAL, ContactDetailType.ADDRESS).forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t.toDisplayLabel()) },
                                onClick = { type = t; typeExpanded = false },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label (e.g. Mobile, Work)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Value") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = when (type) {
                            ContactDetailType.PHONE -> KeyboardType.Phone
                            ContactDetailType.EMAIL -> KeyboardType.Email
                            else -> KeyboardType.Text
                        }
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val detail = existing?.copy(type = type, label = label, value = value)
                        ?: ContactDetail(personId = personId, type = type, label = label, value = value)
                    onSave(detail)
                },
                enabled = value.isNotBlank() && label.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ContactDetailRow(
    detail: ContactDetail,
    onLaunch: (Intent) -> Unit,
    onEdit: (ContactDetail) -> Unit,
    onDelete: (ContactDetail) -> Unit,
) {
    val icon = when (detail.type) {
        ContactDetailType.PHONE -> Icons.Default.Phone
        ContactDetailType.EMAIL -> Icons.Default.Email
        ContactDetailType.SOCIAL -> Icons.Default.Share
        else -> Icons.Default.Home
    }
    val intent: Intent? = when (detail.type) {
        ContactDetailType.PHONE -> Intent(Intent.ACTION_DIAL, Uri.parse("tel:${detail.value}"))
        ContactDetailType.EMAIL -> Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${detail.value}"))
        else -> null
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Column(
            modifier = if (intent != null)
                Modifier.weight(1f).clickable { onLaunch(intent) }
            else
                Modifier.weight(1f)
        ) {
            Text(detail.value, style = MaterialTheme.typography.bodyMedium)
            Text(detail.label, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { onEdit(detail) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Edit, contentDescription = "Edit",
                modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { onDelete(detail) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "Delete",
                modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
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
private fun InteractionsTab(items: List<Interaction>, onEdit: (UUID) -> Unit, onDelete: (Interaction) -> Unit) {
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
private fun ActivitiesTab(items: List<Activity>, onEdit: (UUID) -> Unit, onDelete: (Activity) -> Unit) {
    if (items.isEmpty()) { EmptyTab("No activities yet — tap + to log one"); return }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items, key = { it.id }) { activity ->
            ListItem(
                modifier = Modifier.clickable { onEdit(activity.id) },
                headlineContent = { Text(activity.title) },
                supportingContent = {
                    activity.body?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                },
                overlineContent = { Text(activity.timestamp.toDisplayDate()) },
                trailingContent = {
                    IconButton(onClick = { onDelete(activity) }) {
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
private fun LifeEventsTab(items: List<LifeEvent>, onEdit: (UUID) -> Unit, onDelete: (LifeEvent) -> Unit) {
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
