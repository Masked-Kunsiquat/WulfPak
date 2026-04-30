package com.github.maskedkunisquat.wulfpak.ui.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.maskedkunisquat.wulfpak.ui.common.PersonAvatar
import com.github.maskedkunisquat.wulfpak.ui.common.toDisplayDate
import com.github.maskedkunisquat.wulfpak.ui.common.toDisplayLabel
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityDetailScreen(
    activityId: UUID,
    onNavigateBack: () -> Unit,
    onEdit: (UUID) -> Unit,
    onOpenPerson: (UUID) -> Unit,
    viewModel: ActivityDetailViewModel = viewModel(),
) {
    LaunchedEffect(activityId) { viewModel.load(activityId) }

    val activity     by viewModel.activity.collectAsStateWithLifecycle()
    val participants by viewModel.participants.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(activity?.title ?: "") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    activity?.let { a ->
                        IconButton(onClick = { onEdit(a.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                    }
                },
            )
        },
    ) { padding ->
        activity?.let { a ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    a.timestamp.toDisplayDate(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(a.title, style = MaterialTheme.typography.titleLarge)

                a.body?.let { body ->
                    Spacer(Modifier.height(12.dp))
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            body,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }

                if (participants.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "People",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider(modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
                    participants.forEach { person ->
                        ListItem(
                            modifier = Modifier.clickable { onOpenPerson(person.id) },
                            headlineContent = {
                                val name = buildString {
                                    append(person.firstName)
                                    person.lastName?.let { append(" $it") }
                                }
                                Text(name)
                            },
                            supportingContent = {
                                Text(person.relationLabel.toDisplayLabel())
                            },
                            leadingContent = { PersonAvatar(person, size = 36.dp) },
                        )
                    }
                }
            }
        }
    }
}
