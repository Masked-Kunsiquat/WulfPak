package com.github.maskedkunisquat.wulfpak.ui.me

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.maskedkunisquat.wulfpak.core.data.entity.LifeEvent
import com.github.maskedkunisquat.wulfpak.core.data.entity.LifeEventType
import com.github.maskedkunisquat.wulfpak.core.data.entity.Person
import com.github.maskedkunisquat.wulfpak.core.data.entity.Task
import com.github.maskedkunisquat.wulfpak.ui.common.PersonAvatar
import com.github.maskedkunisquat.wulfpak.ui.common.birthYearIsKnown
import com.github.maskedkunisquat.wulfpak.ui.common.calculateAge
import com.github.maskedkunisquat.wulfpak.ui.common.toDisplayDate
import com.github.maskedkunisquat.wulfpak.ui.common.toDisplayLabel
import com.github.maskedkunisquat.wulfpak.ui.common.toRelativeDisplay
import com.github.maskedkunisquat.wulfpak.ui.feed.FeedItem
import java.util.UUID

private val ME_TABS = listOf("Overview", "Activity", "Relationships", "Tasks")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeScreen(
    onAddTask: () -> Unit,
    onEditTask: (Task) -> Unit,
    viewModel: MeViewModel = viewModel(),
) {
    val me by viewModel.me.collectAsStateWithLifecycle()
    val meLifeEvents by viewModel.meLifeEvents.collectAsStateWithLifecycle()
    val totalContacts by viewModel.totalContacts.collectAsStateWithLifecycle()
    val interactionsThisMonth by viewModel.interactionsThisMonth.collectAsStateWithLifecycle()
    val feed by viewModel.feed.collectAsStateWithLifecycle()
    val personsById by viewModel.personsById.collectAsStateWithLifecycle()
    val rankedContacts by viewModel.rankedContacts.collectAsStateWithLifecycle()
    val lapsingContacts by viewModel.lapsingContacts.collectAsStateWithLifecycle()
    val allOpenTasks by viewModel.allOpenTasks.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Me") }) },
        floatingActionButton = {
            if (me != null && selectedTab == 3) {
                FloatingActionButton(onClick = onAddTask) {
                    Icon(Icons.Default.Add, contentDescription = "Add task")
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (me == null) {
                EmptyMeCard()
            } else {
                val p = me!!
                MeHeader(me = p, lifeEvents = meLifeEvents)
                ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp) {
                    ME_TABS.forEachIndexed { i, title ->
                        Tab(
                            selected = selectedTab == i,
                            onClick  = { selectedTab = i },
                            text     = { Text(title) },
                        )
                    }
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    when (selectedTab) {
                        0 -> OverviewTab(
                            totalContacts         = totalContacts,
                            interactionsThisMonth = interactionsThisMonth,
                            summaryText           = viewModel.meSummaryText,
                            isSummarizing         = viewModel.isSummarizing,
                            summaryGeneratedAt    = viewModel.summaryGeneratedAt,
                            onSummarize           = viewModel::summarizeMe,
                        )
                        1 -> ActivityTab(feed = feed, personsById = personsById)
                        2 -> RelationshipsTab(ranked = rankedContacts, lapsing = lapsingContacts)
                        3 -> TasksTab(
                            items        = allOpenTasks,
                            onToggleDone = { viewModel.toggleTaskDone(it.task) },
                            onEdit       = { onEditTask(it.task) },
                            onDelete     = { viewModel.deleteTask(it.task) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyMeCard() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(modifier = Modifier.padding(32.dp)) {
            Text(
                "Tap ··· on a contact and choose 'This is me'",
                style    = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(24.dp),
            )
        }
    }
}

@Composable
private fun MeHeader(me: Person, lifeEvents: List<LifeEvent>) {
    Row(
        modifier             = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment    = Alignment.CenterVertically,
    ) {
        PersonAvatar(me, size = 56.dp)
        Column {
            val birthday = lifeEvents.firstOrNull { it.eventType == LifeEventType.BIRTHDAY }
            val death    = lifeEvents.firstOrNull { it.eventType == LifeEventType.DEATH }
            val ageLabel = when {
                birthday != null && birthday.date.birthYearIsKnown() && death != null ->
                    "Passed away at ${calculateAge(birthday.date, death.date)}"
                birthday != null && birthday.date.birthYearIsKnown() ->
                    "${calculateAge(birthday.date)} years old"
                else -> ""
            }
            Text(
                listOfNotNull(me.firstName, me.lastName).joinToString(" "),
                style = MaterialTheme.typography.titleMedium,
            )
            if (ageLabel.isNotEmpty()) Text(
                ageLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            me.nickname?.let {
                Text("\"$it\"", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val job = listOfNotNull(me.jobTitle, me.company).joinToString(" at ")
            if (job.isNotBlank()) Text(
                job,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun OverviewTab(
    totalContacts: Int,
    interactionsThisMonth: Int,
    summaryText: String,
    isSummarizing: Boolean,
    summaryGeneratedAt: Long?,
    onSummarize: () -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    "$totalContacts contacts · $interactionsThisMonth interactions this month",
                    style    = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        item {
            MeAiSummaryCard(
                summaryText        = summaryText,
                isSummarizing      = isSummarizing,
                summaryGeneratedAt = summaryGeneratedAt,
                onSummarize        = onSummarize,
            )
        }
    }
}

@Composable
private fun MeAiSummaryCard(
    summaryText: String,
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
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Summary", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    if (summaryGeneratedAt != null && summaryText.isNotEmpty()) {
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
                summaryText.isEmpty() -> Text(
                    "Tap refresh to generate a summary.",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                else -> Text(
                    summaryText,
                    style    = MaterialTheme.typography.bodySmall,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ActivityTab(feed: List<FeedItem>, personsById: Map<UUID, Person>) {
    if (feed.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No activity yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(feed, key = {
            when (it) {
                is FeedItem.InteractionItem -> "i:${it.interaction.id}"
                is FeedItem.ActivityItem    -> "a:${it.activity.id}"
            }
        }) { item ->
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

@Composable
private fun RelationshipsTab(ranked: List<Person>, lapsing: List<Person>) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                "Closest",
                style    = MaterialTheme.typography.labelMedium,
                color    = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        if (ranked.isEmpty()) {
            item {
                Text(
                    "No contacts yet",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        } else {
            items(ranked, key = { "r:${it.id}" }) { person ->
                ListItem(
                    headlineContent = {
                        Text(listOfNotNull(person.firstName, person.lastName).joinToString(" "))
                    },
                    trailingContent = person.closenessScore?.let { score ->
                        {
                            LinearProgressIndicator(
                                progress = { score.coerceIn(0f, 1f) },
                                modifier = Modifier.width(64.dp),
                            )
                        }
                    },
                )
            }
        }
        item {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            Text(
                "Lapsing",
                style    = MaterialTheme.typography.labelMedium,
                color    = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        if (lapsing.isEmpty()) {
            item {
                Text(
                    "Everyone's in touch",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        } else {
            items(lapsing, key = { "l:${it.id}" }) { person ->
                ListItem(
                    headlineContent = {
                        Text(listOfNotNull(person.firstName, person.lastName).joinToString(" "))
                    },
                    supportingContent = {
                        val label = person.lastContactedAt
                            ?.let { "Last contact ${it.toRelativeDisplay()}" }
                            ?: "Never contacted"
                        Text(label)
                    },
                )
            }
        }
    }
}

@Composable
private fun TasksTab(
    items: List<MeViewModel.TaskWithPerson>,
    onToggleDone: (MeViewModel.TaskWithPerson) -> Unit,
    onEdit: (MeViewModel.TaskWithPerson) -> Unit,
    onDelete: (MeViewModel.TaskWithPerson) -> Unit,
) {
    if (items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No open tasks", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items, key = { it.task.id }) { twp ->
            ListItem(
                modifier        = Modifier.clickable { onEdit(twp) },
                headlineContent = {
                    Text(
                        text           = twp.task.title,
                        textDecoration = if (twp.task.isDone) TextDecoration.LineThrough else null,
                    )
                },
                supportingContent = {
                    val namePart = twp.person?.let {
                        listOfNotNull(it.firstName, it.lastName).joinToString(" ")
                    }
                    val duePart  = twp.task.dueAt?.let { "Due ${it.toDisplayDate()}" }
                    val label    = listOfNotNull(namePart, duePart).joinToString(" · ")
                    if (label.isNotEmpty()) Text(label)
                },
                leadingContent  = {
                    IconButton(onClick = { onToggleDone(twp) }) {
                        Icon(
                            imageVector        = if (twp.task.isDone) Icons.Default.CheckBox
                                                 else Icons.Default.CheckBoxOutlineBlank,
                            contentDescription = if (twp.task.isDone) "Mark incomplete" else "Mark done",
                            tint               = if (twp.task.isDone) MaterialTheme.colorScheme.primary
                                                 else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                trailingContent = {
                    IconButton(onClick = { onDelete(twp) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error)
                    }
                },
            )
        }
    }
}
