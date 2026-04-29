package com.github.maskedkunisquat.wulfpak.ui.person

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditNoteScreen(
    personId: String,
    noteId: UUID? = null,
    onNavigateBack: () -> Unit,
    viewModel: AddEditNoteViewModel = viewModel(),
) {
    LaunchedEffect(personId, noteId) { viewModel.load(personId, noteId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (noteId == null) "Add Note" else "Edit Note") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.save(onNavigateBack) },
                        enabled = viewModel.isValid,
                    ) { Text("Save") }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = viewModel.body,
                onValueChange = { viewModel.body = it },
                label = { Text("Note *") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 5,
                maxLines = 20,
            )
        }
    }
}
