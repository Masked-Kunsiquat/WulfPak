package com.github.maskedkunisquat.wulfpak.ui.person

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.github.maskedkunisquat.wulfpak.core.data.entity.GiftStatus
import java.util.UUID

private val ALL_GIFT_STATUSES = listOf(GiftStatus.IDEA, GiftStatus.PURCHASED, GiftStatus.GIVEN)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditGiftScreen(
    personId: String,
    giftId: UUID? = null,
    onNavigateBack: () -> Unit,
    viewModel: AddEditGiftViewModel = viewModel(),
) {
    LaunchedEffect(personId, giftId) { viewModel.load(personId, giftId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (giftId == null) "Add Gift" else "Edit Gift") },
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
                value = viewModel.name,
                onValueChange = { viewModel.name = it },
                label = { Text("Gift name *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = viewModel.occasion,
                onValueChange = { viewModel.occasion = it },
                label = { Text("Occasion") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Text("Status", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ALL_GIFT_STATUSES.forEach { s ->
                    FilterChip(
                        selected = viewModel.status == s,
                        onClick = { viewModel.status = s },
                        label = { Text(s.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }

            OutlinedTextField(
                value = viewModel.note,
                onValueChange = { viewModel.note = it },
                label = { Text("Note") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 5,
            )
        }
    }
}
