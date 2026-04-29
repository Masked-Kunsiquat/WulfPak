package com.github.maskedkunisquat.wulfpak.ui.people

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.maskedkunisquat.wulfpak.core.data.entity.RelationLabel
import com.github.maskedkunisquat.wulfpak.ui.common.toDisplayLabel
import java.util.UUID

private val ALL_RELATION_LABELS = RelationLabel.ALL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditPersonScreen(
    personId: UUID? = null,
    onNavigateBack: () -> Unit,
    viewModel: AddEditPersonViewModel = viewModel(),
) {
    LaunchedEffect(personId) { personId?.let { viewModel.load(it) } }

    val photoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.photoUri = it.toString() } }

    var relationExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (personId == null) "Add Person" else "Edit Person") },
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
                value = viewModel.firstName,
                onValueChange = { viewModel.firstName = it },
                label = { Text("First name *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = viewModel.lastName,
                onValueChange = { viewModel.lastName = it },
                label = { Text("Last name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = viewModel.nickname,
                onValueChange = { viewModel.nickname = it },
                label = { Text("Nickname") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            ExposedDropdownMenuBox(
                expanded = relationExpanded,
                onExpandedChange = { relationExpanded = it },
            ) {
                OutlinedTextField(
                    value = viewModel.relationLabel.toDisplayLabel(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Relation") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = relationExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(
                    expanded = relationExpanded,
                    onDismissRequest = { relationExpanded = false },
                ) {
                    ALL_RELATION_LABELS.forEach { label ->
                        DropdownMenuItem(
                            text = { Text(label.toDisplayLabel()) },
                            onClick = { viewModel.relationLabel = label; relationExpanded = false },
                        )
                    }
                }
            }

            Text("Closeness rating", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row {
                (1..5).forEach { star ->
                    val filled = (viewModel.closenessRating ?: 0) >= star
                    IconButton(onClick = {
                        viewModel.closenessRating = if (viewModel.closenessRating == star) null else star
                    }) {
                        Icon(
                            imageVector = if (filled) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "$star stars",
                            tint = if (filled) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = { photoLauncher.launch("image/*") },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (viewModel.photoUri != null) "Photo selected ✓" else "Pick photo")
            }
        }
    }
}
