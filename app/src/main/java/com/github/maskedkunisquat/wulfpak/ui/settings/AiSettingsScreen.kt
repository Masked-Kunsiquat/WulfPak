package com.github.maskedkunisquat.wulfpak.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.maskedkunisquat.wulfpak.core.logic.llm.ModelLoadState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel,
) {
    val modelLoadState    by viewModel.modelLoadState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.downloadError) {
        viewModel.downloadError?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearDownloadError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val modelReady     = modelLoadState == ModelLoadState.READY
        val modelLoading   = modelLoadState == ModelLoadState.LOADING_SESSION
        val modelAvailable = viewModel.isModelAvailable
        val progress       = viewModel.downloadProgress
        val count          = viewModel.pendingEmbedCount
        val embedding      = viewModel.isEmbedding

        Column(modifier = Modifier.padding(padding)) {
            ListItem(
                headlineContent = {
                    Text(when {
                        modelReady       -> "AI model ready"
                        modelLoading     -> "Loading model…"
                        progress != null -> "Downloading AI model ($progress%)"
                        modelAvailable   -> "AI model downloaded"
                        else             -> "Download AI model"
                    })
                },
                supportingContent = {
                    Column {
                        Text(when {
                            modelReady       -> "On-device Gemma 4 E4B is loaded and ready"
                            modelLoading     -> "Initializing model engine…"
                            progress != null -> "Download in progress…"
                            modelAvailable   -> "Tap to load into memory"
                            else             -> "Gemma 4 E4B (~3.65 GB) — required for AI features"
                        })
                        if (progress != null) {
                            LinearProgressIndicator(
                                progress = { progress / 100f },
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            )
                        }
                    }
                },
                leadingContent = {
                    if (modelLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    else Icon(Icons.Default.Download, contentDescription = null)
                },
                modifier = Modifier.clickable(enabled = !modelReady && !modelLoading && progress == null) {
                    if (modelAvailable) viewModel.loadModel()
                    else viewModel.downloadModel()
                },
            )

            ListItem(
                headlineContent   = { Text("Search index") },
                supportingContent = {
                    Text(when {
                        embedding     -> "Indexing…"
                        count == null -> "Calculating…"
                        count == 0    -> "Fully indexed"
                        else          -> "$count item${if (count == 1) "" else "s"} pending indexing"
                    })
                },
                leadingContent = {
                    if (embedding) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    else Icon(Icons.Default.Search, contentDescription = null)
                },
                trailingContent = {
                    if (!embedding && count != null && count > 0) {
                        TextButton(onClick = viewModel::triggerEmbedding) { Text("Index now") }
                    }
                },
            )
        }
    }
}
