package com.github.maskedkunisquat.wulfpak.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateSecurity: () -> Unit,
    onNavigateDisplay: () -> Unit,
    onNavigateAi: () -> Unit,
    onNavigateContacts: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                headlineContent   = { Text("Security") },
                supportingContent = { Text("Biometric lock") },
                leadingContent    = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingContent   = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                modifier          = Modifier.clickable(onClick = onNavigateSecurity),
            )
            ListItem(
                headlineContent   = { Text("Display") },
                supportingContent = { Text("Birthday ages and appearance") },
                leadingContent    = { Icon(Icons.Default.Tune, contentDescription = null) },
                trailingContent   = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                modifier          = Modifier.clickable(onClick = onNavigateDisplay),
            )
            ListItem(
                headlineContent   = { Text("AI") },
                supportingContent = { Text("On-device model and search index") },
                leadingContent    = { Icon(Icons.Default.Memory, contentDescription = null) },
                trailingContent   = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                modifier          = Modifier.clickable(onClick = onNavigateAi),
            )
            ListItem(
                headlineContent   = { Text("Contacts") },
                supportingContent = { Text("Import, export, and merge") },
                leadingContent    = { Icon(Icons.Default.People, contentDescription = null) },
                trailingContent   = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                modifier          = Modifier.clickable(onClick = onNavigateContacts),
            )
        }
    }
}
