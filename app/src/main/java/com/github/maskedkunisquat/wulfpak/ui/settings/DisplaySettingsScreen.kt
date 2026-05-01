package com.github.maskedkunisquat.wulfpak.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisplaySettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel,
) {
    val showBirthdayAge by viewModel.showBirthdayAge.collectAsStateWithLifecycle()
    val sortByLastName  by viewModel.sortByLastName.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Display") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            ListItem(
                headlineContent   = { Text("Show age on contact") },
                supportingContent = { Text("Display birthday age on the person detail header") },
                leadingContent    = { Icon(Icons.Default.Cake, contentDescription = null) },
                trailingContent   = {
                    Switch(
                        checked         = showBirthdayAge,
                        onCheckedChange = { viewModel.setShowBirthdayAge(it) },
                    )
                },
            )
            ListItem(
                headlineContent   = { Text("Sort contacts by last name") },
                supportingContent = { Text("Lists and pickers sort A–Z by last name instead of first") },
                leadingContent    = { Icon(Icons.Default.SortByAlpha, contentDescription = null) },
                trailingContent   = {
                    Switch(
                        checked         = sortByLastName,
                        onCheckedChange = { viewModel.setSortByLastName(it) },
                    )
                },
            )
        }
    }
}
