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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.maskedkunisquat.wulfpak.core.data.dao.PersonConnection
import com.github.maskedkunisquat.wulfpak.core.data.entity.Activity
import com.github.maskedkunisquat.wulfpak.core.data.entity.ContactDetail
import com.github.maskedkunisquat.wulfpak.core.data.entity.ContactDetailType
import com.github.maskedkunisquat.wulfpak.core.data.entity.FamilyRelType
import com.github.maskedkunisquat.wulfpak.core.data.entity.Gift
import com.github.maskedkunisquat.wulfpak.core.data.entity.Interaction
import com.github.maskedkunisquat.wulfpak.AppPrefsKeys
import com.github.maskedkunisquat.wulfpak.appDataStore
import com.github.maskedkunisquat.wulfpak.core.data.entity.LifeEvent
import com.github.maskedkunisquat.wulfpak.core.data.entity.LifeEventType
import com.github.maskedkunisquat.wulfpak.core.data.entity.Note
import com.github.maskedkunisquat.wulfpak.core.data.entity.Person
import com.github.maskedkunisquat.wulfpak.core.data.entity.RelCategory
import com.github.maskedkunisquat.wulfpak.core.data.entity.Task
import com.github.maskedkunisquat.wulfpak.core.logic.family.InferredKin
import com.github.maskedkunisquat.wulfpak.ui.common.PersonAvatar
import com.github.maskedkunisquat.wulfpak.ui.common.birthYearIsKnown
import com.github.maskedkunisquat.wulfpak.ui.common.calculateAge
import com.github.maskedkunisquat.wulfpak.ui.common.toDisplayDate
import com.github.maskedkunisquat.wulfpak.ui.common.toDisplayLabel
import java.util.UUID
import kotlinx.coroutines.flow.map

private val TABS = listOf("Interactions", "Activities", "Notes", "Life Events", "Gifts", "Tasks", "Connections")

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
    onNavigateToPerson: (UUID) -> Unit,
) {
    val context          = LocalContext.current
    val showBirthdayAge  by context.appDataStore.data
        .map { prefs -> prefs[AppPrefsKeys.SHOW_BIRTHDAY_AGE] ?: true }
        .collectAsStateWithLifecycle(initialValue = true)
    val person           by viewModel.person.collectAsStateWithLifecycle()
    val interactions   by viewModel.interactions.collectAsStateWithLifecycle()
    val activities     by viewModel.activities.collectAsStateWithLifecycle()
    val notes          by viewModel.notes.collectAsStateWithLifecycle()
    val lifeEvents     by viewModel.lifeEvents.collectAsStateWithLifecycle()
    val gifts          by viewModel.gifts.collectAsStateWithLifecycle()
    val tasks          by viewModel.tasks.collectAsStateWithLifecycle()
    val contactDetails  by viewModel.contactDetails.collectAsStateWithLifecycle()
    val connections     by viewModel.connections.collectAsStateWithLifecycle()
    val inferredKin     by viewModel.inferredKin.collectAsStateWithLifecycle()

    var selectedTab        by remember { mutableIntStateOf(0) }
    var showOverflow       by remember { mutableStateOf(false) }
    var showDeleteConfirm  by remember { mutableStateOf(false) }
    var contactsExpanded   by remember { mutableStateOf(true) }
    var showAddDetail      by remember { mutableStateOf(false) }
    var editingDetail      by remember { mutableStateOf<ContactDetail?>(null) }
    var showAddConnection  by remember { mutableStateOf(false) }

    val fabAction: (() -> Unit)? = when (selectedTab) {
        0 -> onAddInteraction
        1 -> onAddActivity
        2 -> onAddNote
        3 -> onAddLifeEvent
        4 -> onAddGift
        5 -> onAddTask
        6 -> { { viewModel.loadAllPersons(); showAddConnection = true } }
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
        if (showAddConnection) {
            AddConnectionDialog(
                allPersons      = viewModel.allPersons,
                currentPersonId = p.id,
                onSave          = { otherId, label, category, relType ->
                    viewModel.addConnection(otherId, label, category, relType)
                    showAddConnection = false
                },
                onDismiss       = { showAddConnection = false },
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
                        val birthday = lifeEvents.firstOrNull { it.eventType == LifeEventType.BIRTHDAY }
                        val death    = lifeEvents.firstOrNull { it.eventType == LifeEventType.DEATH }
                        val ageLabel = if (!showBirthdayAge) "" else when {
                            birthday != null && birthday.date.birthYearIsKnown() && death != null ->
                                " · Passed away at ${calculateAge(birthday.date, death.date)}"
                            birthday != null && birthday.date.birthYearIsKnown() ->
                                " · ${calculateAge(birthday.date)} years old"
                            else -> ""
                        }
                        Text(p.relationLabel.toDisplayLabel() + ageLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary)
                        p.nickname?.let { Text("\"$it\"", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        val job = listOfNotNull(p.jobTitle, p.company).joinToString(" at ")
                        if (job.isNotBlank()) Text(job, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    summarizeText      = viewModel.summarizeText,
                    isSummarizing      = viewModel.isSummarizing,
                    summaryGeneratedAt = viewModel.summaryGeneratedAt,
                    onSummarize        = viewModel::summarize,
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
                    6 -> ConnectionsTab(connections, inferredKin = inferredKin,
                        onNavigateToPerson = onNavigateToPerson, onDelete = viewModel::removeConnection)
                }
            }
        }
    }
}

@Composable
private fun AiSummaryCard(
    summarizeText: String,
    isSummarizing: Boolean,
    summaryGeneratedAt: Long?,
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
                Column {
                    Text("Summary", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    if (summaryGeneratedAt != null && summarizeText.isNotEmpty()) {
                        Text(
                            "Updated ${summaryGeneratedAt.toDisplayDate()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
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
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Remove contact info?") },
            text = { Text(detail.value) },
            confirmButton = {
                TextButton(onClick = { onDelete(detail); showDeleteConfirm = false }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }

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
        Box {
            IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.MoreVert, contentDescription = "More",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("Edit") },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    onClick = { menuExpanded = false; onEdit(detail) },
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = {
                        Icon(Icons.Default.Delete, contentDescription = null,
                            tint = MaterialTheme.colorScheme.error)
                    },
                    onClick = { menuExpanded = false; showDeleteConfirm = true },
                )
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
private fun ConnectionsTab(
    items: List<PersonConnection>,
    inferredKin: List<InferredKin>,
    onNavigateToPerson: (UUID) -> Unit,
    onDelete: (UUID) -> Unit,
) {
    if (items.isEmpty()) { EmptyTab("No connections — tap + to add one"); return }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items, key = { it.otherId }) { conn ->
            val name = "${conn.firstName}${conn.lastName?.let { " $it" } ?: ""}"
            val chipLabel = when (conn.category) {
                RelCategory.FAMILY.name -> "Family"
                RelCategory.FRIEND.name -> "Friend"
                RelCategory.WORK.name -> "Work"
                else -> null
            }
            ListItem(
                modifier = Modifier.clickable { onNavigateToPerson(conn.otherId) },
                headlineContent = { Text("$name · ${conn.effectiveLabel}") },
                supportingContent = if (chipLabel != null) {
                    { SuggestionChip(onClick = {}, label = { Text(chipLabel, style = MaterialTheme.typography.labelSmall) }) }
                } else null,
                trailingContent = {
                    IconButton(onClick = { onDelete(conn.otherId) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error)
                    }
                },
            )
        }
        if (inferredKin.isNotEmpty()) {
            item {
                Text(
                    "Inferred family",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                )
            }
            items(inferredKin, key = { "inferred_${it.personId}" }) { kin ->
                ListItem(
                    headlineContent = {
                        Text("${kin.name} · ${kin.kinLabel}", fontStyle = FontStyle.Italic)
                    },
                    trailingContent = {
                        SuggestionChip(
                            onClick = {},
                            label = { Text("inferred", style = MaterialTheme.typography.labelSmall) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                            border = null,
                        )
                    },
                )
            }
        }
    }
}

private val FRIEND_LABELS = listOf("Friend", "Best Friend", "Family Friend", "Introduced me")
private val WORK_LABELS = listOf("Colleague", "Manager", "Direct Report", "Client", "Mentor")
private val FAMILY_LABELS: List<String> = FamilyRelType.entries
    .flatMap { t -> if (t.displayLabel == t.reverseLabel) listOf(t.displayLabel) else listOf(t.displayLabel, t.reverseLabel) }
    .distinct()
private val FAMILY_LABEL_TO_REL_TYPE: Map<String, FamilyRelType> = buildMap {
    FamilyRelType.entries.forEach { t ->
        require(!contains(t.displayLabel)) { "FAMILY_LABEL_TO_REL_TYPE key collision on \"${t.displayLabel}\"" }
        put(t.displayLabel, t)
        if (t.reverseLabel != t.displayLabel) {
            require(!contains(t.reverseLabel)) { "FAMILY_LABEL_TO_REL_TYPE key collision on \"${t.reverseLabel}\"" }
            put(t.reverseLabel, t)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddConnectionDialog(
    allPersons: List<Person>,
    currentPersonId: UUID,
    onSave: (otherId: UUID, label: String, category: String, relType: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val candidates = allPersons.filter { it.id != currentPersonId }
    var selectedPerson   by remember { mutableStateOf<Person?>(null) }
    var personExpanded   by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableIntStateOf(0) } // 0=Friends, 1=Family, 2=Work, 3=Other
    var selectedLabel    by remember { mutableStateOf(FRIEND_LABELS[0]) }
    var labelExpanded    by remember { mutableStateOf(false) }
    var customLabel      by remember { mutableStateOf("") }

    val currentLabels = when (selectedCategory) {
        1    -> FAMILY_LABELS
        2    -> WORK_LABELS
        3    -> emptyList()
        else -> FRIEND_LABELS
    }
    val showCustom = selectedCategory == 3 || (selectedCategory != 1 && selectedLabel == "Custom…")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Connection") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(expanded = personExpanded, onExpandedChange = { personExpanded = it }) {
                    OutlinedTextField(
                        value = selectedPerson?.let { "${it.firstName}${it.lastName?.let { n -> " $n" } ?: ""}" } ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Person") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = personExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(expanded = personExpanded, onDismissRequest = { personExpanded = false }) {
                        candidates.forEach { person ->
                            val name = "${person.firstName}${person.lastName?.let { " $it" } ?: ""}"
                            DropdownMenuItem(text = { Text(name) },
                                onClick = { selectedPerson = person; personExpanded = false })
                        }
                    }
                }
                val categoryLabels = listOf("Friends", "Family", "Work", "Other")
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    categoryLabels.forEachIndexed { index, label ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = categoryLabels.size),
                            onClick = {
                                if (selectedCategory != index) {
                                    selectedCategory = index
                                    selectedLabel = when (index) {
                                        1    -> FAMILY_LABELS[0]
                                        2    -> WORK_LABELS[0]
                                        3    -> ""
                                        else -> FRIEND_LABELS[0]
                                    }
                                    customLabel = ""
                                    labelExpanded = false
                                }
                            },
                            selected = selectedCategory == index,
                        ) { Text(label) }
                    }
                }
                if (selectedCategory != 3) {
                    ExposedDropdownMenuBox(expanded = labelExpanded, onExpandedChange = { labelExpanded = it }) {
                        OutlinedTextField(
                            value = selectedLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Relationship") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = labelExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        )
                        ExposedDropdownMenu(expanded = labelExpanded, onDismissRequest = { labelExpanded = false }) {
                            currentLabels.forEach { lbl ->
                                DropdownMenuItem(text = { Text(lbl) },
                                    onClick = { selectedLabel = lbl; labelExpanded = false })
                            }
                            if (selectedCategory != 1) {
                                DropdownMenuItem(text = { Text("Custom…") },
                                    onClick = { selectedLabel = "Custom…"; labelExpanded = false })
                            }
                        }
                    }
                }
                if (showCustom) {
                    OutlinedTextField(
                        value = customLabel,
                        onValueChange = { customLabel = it },
                        label = { Text("Custom label") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = {
            val finalLabel = if (showCustom) customLabel else selectedLabel
            val category = when (selectedCategory) {
                1    -> RelCategory.FAMILY.name
                2    -> RelCategory.WORK.name
                3    -> RelCategory.OTHER.name
                else -> RelCategory.FRIEND.name
            }
            val relType = if (selectedCategory == 1) FAMILY_LABEL_TO_REL_TYPE[selectedLabel]?.name else null
            TextButton(
                onClick = { selectedPerson?.let { onSave(it.id, finalLabel, category, relType) } },
                enabled = selectedPerson != null && (selectedCategory == 1 || selectedCategory != 3 && selectedLabel != "Custom…" || customLabel.isNotBlank()),
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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
