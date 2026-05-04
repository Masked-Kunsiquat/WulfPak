package com.github.maskedkunisquat.wulfpak.ui.debug

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugSummaryScreen(
    onNavigateBack: () -> Unit,
    vm: DebugSummaryViewModel = viewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menuExpanded by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { vm.exportToUri(context, it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debug Capture") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { vm.clear(); onNavigateBack() }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear")
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Export")
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Share as text") },
                                onClick = {
                                    menuExpanded = false
                                    scope.launch {
                                        val text = vm.generateTextSummary()
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, text)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Share debug summary"))
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Export JSON") },
                                onClick = {
                                    menuExpanded = false
                                    val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                    exportLauncher.launch("wulfpak_debug_$ts.json")
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (vm.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val s = vm.summary
        if (s == null || s.totalEvents == 0) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No events captured yet.", style = MaterialTheme.typography.bodyMedium)
            }
            return@Scaffold
        }

        val sdf = SimpleDateFormat("MMM d HH:mm", Locale.US)
        val spanH = if (s.periodStart != null && s.periodEnd != null)
            (s.periodEnd - s.periodStart) / 3_600_000.0 else 0.0

        LazyColumn(
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            item { Spacer(Modifier.height(4.dp)) }

            // Header
            item {
                Column {
                    Text(
                        "${s.totalEvents} events",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    if (s.periodStart != null && s.periodEnd != null) {
                        Text(
                            "${sdf.format(Date(s.periodStart))} → ${sdf.format(Date(s.periodEnd))}  " +
                                "(${"%.1f".format(spanH)}h)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Flags — shown first so problems are immediately visible
            if (s.flags.isNotEmpty()) {
                item {
                    SectionCard(title = "⚠ Flags (${s.flags.size})") {
                        s.flags.forEach { flag ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 2.dp),
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                                Text(
                                    flag,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    }
                }
            }

            // LLM Queries
            s.queryStats?.let { q ->
                item {
                    SectionCard(title = "LLM Queries (${q.count})") {
                        StatRow("Avg duration", fmtMs(q.avgDurationMs))
                        StatRow("Max duration", fmtMs(q.maxDurationMs))
                        StatRow("Avg tools / query", "%.1f".format(q.avgTools))
                        StatRow("Max tools / query", "${q.maxTools}")
                        if (q.toolBreakdown.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(8.dp))
                            Text("Tool breakdown", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val total = q.toolBreakdown.sumOf { it.second }
                            q.toolBreakdown.take(8).forEach { (tool, cnt) ->
                                val pct = if (total > 0) (100 * cnt / total) else 0
                                ToolRow(tool, cnt, pct)
                            }
                        }
                    }
                }
            }

            // LLM Summarize
            s.summarizeStats?.let { ss ->
                item {
                    SectionCard(title = "LLM Summarize (${ss.count})") {
                        StatRow("Avg duration", fmtMs(ss.avgDurationMs))
                        StatRow("Success", "${ss.successCount} / ${ss.count}")
                    }
                }
            }

            // Embedding Runs
            s.embeddingStats?.let { e ->
                item {
                    SectionCard(title = "Embedding Runs (${e.count})") {
                        StatRow("Avg items / run", "%.1f".format(e.avgItems))
                        StatRow("Avg duration", fmtMs(e.avgDurationMs))
                        e.resultBreakdown.entries.sortedBy { it.key }.forEach { (result, cnt) ->
                            StatRow(result, "$cnt")
                        }
                    }
                }
            }

            // Search Queries
            s.searchStats?.let { sq ->
                item {
                    SectionCard(title = "Search Queries (${sq.count})") {
                        StatRow("Avg duration", fmtMs(sq.avgDurationMs))
                        StatRow("Avg results", "%.1f".format(sq.avgResults))
                        StatRow("Zero-vector hits", "${sq.zeroVectorCount}")
                    }
                }
            }

            // Backup
            if (s.backups.isNotEmpty()) {
                item {
                    SectionCard(title = "Backup (${s.backups.size})") {
                        s.backups.forEach { b ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    "${sdf.format(Date(b.ts))}  ${b.op}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                )
                                Text(
                                    "${b.recordCount} records  ${fmtMs(b.durationMs)}  ${if (b.success) "ok" else "FAIL"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (b.success) MaterialTheme.colorScheme.onSurface
                                            else MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }

            // Navigation
            if (s.navTransitions.isNotEmpty()) {
                item {
                    SectionCard(title = "Navigation (top flows)") {
                        s.navTransitions.forEach { (label, cnt) ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(label, style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.weight(1f))
                                Spacer(Modifier.width(8.dp))
                                Text("$cnt", style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Biometric
            if (s.biometricResults.isNotEmpty()) {
                item {
                    SectionCard(title = "Biometric") {
                        s.biometricResults.entries.sortedBy { it.key }.forEach { (result, cnt) ->
                            StatRow(result, "$cnt")
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ToolRow(tool: String, count: Int, pct: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            tool,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "$count ($pct%)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

