package com.github.maskedkunisquat.wulfpak.ui.people

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.maskedkunisquat.wulfpak.core.data.entity.Person
import com.github.maskedkunisquat.wulfpak.ui.common.PersonAvatar
import com.github.maskedkunisquat.wulfpak.ui.common.toDisplayLabel
import com.github.maskedkunisquat.wulfpak.ui.common.toRelativeDisplay
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleListScreen(
    onAddPerson: () -> Unit,
    onOpenPerson: (UUID) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: PeopleListViewModel = viewModel(),
) {
    val people by viewModel.people.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WulfPak") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddPerson) {
                Icon(Icons.Default.Add, contentDescription = "Add person")
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
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
                        PersonRow(person, onOpenPerson, viewModel::toggleFavorite)
                    }
                    if (rest.isNotEmpty()) item {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
                if (rest.isNotEmpty()) {
                    if (favorites.isNotEmpty()) item { SectionLabel("All") }
                    items(rest, key = { it.id }) { person ->
                        PersonRow(person, onOpenPerson, viewModel::toggleFavorite)
                    }
                }
            }
            item { Spacer(Modifier.height(88.dp)) } // FAB clearance
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

@Composable
private fun PersonRow(
    person: Person,
    onOpen: (UUID) -> Unit,
    onToggleFavorite: (Person) -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable { onOpen(person.id) },
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
        leadingContent = { PersonAvatar(person) },
        trailingContent = {
            IconButton(onClick = { onToggleFavorite(person) }) {
                Icon(
                    imageVector = if (person.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = null,
                    tint = if (person.isFavorite) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}
