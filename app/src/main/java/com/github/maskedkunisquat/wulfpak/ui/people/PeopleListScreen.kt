package com.github.maskedkunisquat.wulfpak.ui.people

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.maskedkunisquat.wulfpak.core.data.entity.Person
import com.github.maskedkunisquat.wulfpak.core.data.entity.RelationLabel
import com.github.maskedkunisquat.wulfpak.ui.common.PersonAvatar
import com.github.maskedkunisquat.wulfpak.ui.common.toDisplayLabel
import com.github.maskedkunisquat.wulfpak.ui.common.toRelativeDisplay
import java.util.UUID

// High intended closeness but unexpectedly low behavioral score.
// threshold = (rating-1)/4f - 0.15f  →  rating 4 → 0.60, rating 5 → 0.85.
// The -0.15f margin avoids false positives when contact cadence is slightly off.
private val Person.isDrifting: Boolean
    get() {
        val rating = closenessRating ?: return false
        val score = closenessScore ?: return false
        return rating >= 4 && score < (rating - 1) / 4f - 0.15f
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleListScreen(
    onAddPerson: () -> Unit,
    onOpenPerson: (UUID) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPendingCalls: () -> Unit,
    viewModel: PeopleListViewModel = viewModel(),
) {
    val people           by viewModel.people.collectAsStateWithLifecycle()
    val searchQuery      by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedIds      by viewModel.selectedIds.collectAsStateWithLifecycle()
    val pendingCallCount by viewModel.pendingCallCount.collectAsStateWithLifecycle()
    val inMultiSelect = selectedIds.isNotEmpty()

    var showRelationDialog by remember { mutableStateOf(false) }

    if (showRelationDialog) {
        BulkRelationDialog(
            onConfirm = { relation -> viewModel.bulkSetRelation(relation); showRelationDialog = false },
            onDismiss = { showRelationDialog = false },
        )
    }

    Scaffold(
        topBar = {
            if (inMultiSelect) {
                TopAppBar(
                    title = { Text("${selectedIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showRelationDialog = true }) {
                            Icon(Icons.Default.Group, contentDescription = "Set relation")
                        }
                        IconButton(onClick = viewModel::bulkDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete selected",
                                tint = MaterialTheme.colorScheme.error)
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text("WulfPak") },
                    actions = {
                        if (pendingCallCount > 0) {
                            BadgedBox(badge = { Badge { Text("$pendingCallCount") } }) {
                                IconButton(onClick = onOpenPendingCalls) {
                                    Icon(Icons.Default.Phone, contentDescription = "Pending calls")
                                }
                            }
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (!inMultiSelect) {
                FloatingActionButton(onClick = onAddPerson) {
                    Icon(Icons.Default.Add, contentDescription = "Add person")
                }
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            if (!inMultiSelect) {
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = viewModel::setSearchQuery,
                        placeholder = { Text("Search people…") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        },
                    )
                }
            }

            if (people.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (searchQuery.isBlank()) "No people yet — tap + to add someone"
                                   else "No results for \"$searchQuery\"",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                val favorites = if (searchQuery.isBlank()) people.filter { it.isFavorite } else emptyList()
                val rest = if (searchQuery.isBlank()) people.filter { !it.isFavorite } else people

                if (favorites.isNotEmpty()) {
                    item { SectionLabel("Favorites") }
                    items(favorites, key = { it.id }) { person ->
                        PersonRow(
                            person = person,
                            inMultiSelect = inMultiSelect,
                            isSelected = person.id in selectedIds,
                            onOpen = onOpenPerson,
                            onToggleFavorite = viewModel::toggleFavorite,
                            onLongPress = { viewModel.enterMultiSelect(person) },
                            onToggleSelection = viewModel::toggleSelection,
                        )
                    }
                    if (rest.isNotEmpty()) item {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
                if (rest.isNotEmpty()) {
                    if (favorites.isNotEmpty()) item { SectionLabel("All") }
                    items(rest, key = { it.id }) { person ->
                        PersonRow(
                            person = person,
                            inMultiSelect = inMultiSelect,
                            isSelected = person.id in selectedIds,
                            onOpen = onOpenPerson,
                            onToggleFavorite = viewModel::toggleFavorite,
                            onLongPress = { viewModel.enterMultiSelect(person) },
                            onToggleSelection = viewModel::toggleSelection,
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 2.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PersonRow(
    person: Person,
    inMultiSelect: Boolean,
    isSelected: Boolean,
    onOpen: (UUID) -> Unit,
    onToggleFavorite: (Person) -> Unit,
    onLongPress: () -> Unit,
    onToggleSelection: (Person) -> Unit,
) {
    ListItem(
        modifier = Modifier.combinedClickable(
            onClick = { if (inMultiSelect) onToggleSelection(person) else onOpen(person.id) },
            onLongClick = onLongPress,
        ),
        headlineContent = {
            Text("${person.firstName}${person.lastName?.let { " $it" } ?: ""}")
        },
        supportingContent = {
            Text(
                text = person.relationLabel.toDisplayLabel() +
                    (person.lastContactedAt?.let { " · ${it.toRelativeDisplay()}" } ?: ""),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            if (inMultiSelect) {
                Checkbox(checked = isSelected, onCheckedChange = { onToggleSelection(person) })
            } else {
                PersonAvatar(person)
            }
        },
        trailingContent = {
            if (!inMultiSelect) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (person.isDrifting) {
                        Text(
                            text = "⚠ Drifting",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    IconButton(onClick = { onToggleFavorite(person) }) {
                        Icon(
                            imageVector = if (person.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = if (person.isFavorite) "Remove from favorites" else "Add to favorites",
                            tint = if (person.isFavorite) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun BulkRelationDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(RelationLabel.ACQUAINTANCE) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set relationship for selected") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                items(RelationLabel.ALL) { label ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == label, onClick = { selected = label })
                        Text(label.toDisplayLabel(), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
