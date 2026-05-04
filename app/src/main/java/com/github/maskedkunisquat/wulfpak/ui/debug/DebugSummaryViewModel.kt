package com.github.maskedkunisquat.wulfpak.ui.debug

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.maskedkunisquat.wulfpak.AppApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class LlmQueryStats(
    val count: Int,
    val avgDurationMs: Long,
    val maxDurationMs: Long,
    val avgTools: Float,
    val maxTools: Int,
    val toolBreakdown: List<Pair<String, Int>>,
)

data class LlmSummarizeStats(
    val count: Int,
    val avgDurationMs: Long,
    val successCount: Int,
)

data class EmbeddingStats(
    val count: Int,
    val avgItems: Float,
    val avgDurationMs: Long,
    val resultBreakdown: Map<String, Int>,
)

data class SearchStats(
    val count: Int,
    val avgDurationMs: Long,
    val avgResults: Float,
    val zeroVectorCount: Int,
)

data class BackupEntry(
    val ts: Long,
    val op: String,
    val recordCount: Int,
    val durationMs: Long,
    val success: Boolean,
    val error: String? = null,
)

data class DebugSummary(
    val periodStart: Long?,
    val periodEnd: Long?,
    val totalEvents: Int,
    val queryStats: LlmQueryStats?,
    val summarizeStats: LlmSummarizeStats?,
    val embeddingStats: EmbeddingStats?,
    val searchStats: SearchStats?,
    val backups: List<BackupEntry>,
    val navTransitions: List<Pair<String, Int>>,
    val biometricResults: Map<String, Int>,
    val flags: List<String>,
)

class DebugSummaryViewModel(app: Application) : AndroidViewModel(app) {

    private val logger = getApplication<AppApplication>().debugEventLogger
    private var refreshJob: Job? = null

    var isLoading by mutableStateOf(true)
        private set

    var summary by mutableStateOf<DebugSummary?>(null)
        private set

    init { refresh() }

    fun refresh() {
        refreshJob?.cancel()
        isLoading = true
        refreshJob = viewModelScope.launch(Dispatchers.IO) {
            val result = buildSummary(logger.readAllJsonObjects())
            ensureActive()
            summary = result
            isLoading = false
        }
    }

    fun clear() {
        refreshJob?.cancel()
        isLoading = false
        summary = DebugSummary(null, null, 0, null, null, null, null, emptyList(), emptyList(), emptyMap(), emptyList())
        viewModelScope.launch(Dispatchers.IO) { logger.clear() }
    }

    fun exportToUri(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) { logger.exportToUri(context, uri) }
    }

    suspend fun generateTextSummary(): String = withContext(Dispatchers.IO) { logger.generateTextSummary() }

    private fun buildSummary(events: List<JSONObject>): DebugSummary {
        if (events.isEmpty()) return DebugSummary(null, null, 0, null, null, null, null, emptyList(), emptyList(), emptyMap(), emptyList())

        val byType = events.groupBy { it.optString("type") }
        val timestamps = events.mapNotNull { runCatching { it.getLong("ts") }.getOrNull() }

        val queryStats = byType["LLM_QUERY"]?.let { list ->
            val payloads = list.mapNotNull { it.optJSONObject("payload") }
            val durations = payloads.map { it.optLong("duration_ms") }
            val toolCounts = payloads.map { it.optInt("tool_call_count") }
            val allTools = payloads.flatMap { p ->
                val arr = p.optJSONArray("tools_used") ?: return@flatMap emptyList()
                (0 until arr.length()).map { arr.getString(it) }
            }
            LlmQueryStats(
                count = list.size,
                avgDurationMs = if (durations.isEmpty()) 0 else durations.average().toLong(),
                maxDurationMs = durations.maxOrNull() ?: 0,
                avgTools = if (toolCounts.isEmpty()) 0f else toolCounts.average().toFloat(),
                maxTools = toolCounts.maxOrNull() ?: 0,
                toolBreakdown = allTools.groupingBy { it }.eachCount()
                    .entries.sortedByDescending { it.value }
                    .map { it.key to it.value },
            )
        }

        val summarizeStats = byType["LLM_SUMMARIZE"]?.let { list ->
            val payloads = list.mapNotNull { it.optJSONObject("payload") }
            val durations = payloads.map { it.optLong("duration_ms") }
            LlmSummarizeStats(
                count = list.size,
                avgDurationMs = if (durations.isEmpty()) 0 else durations.average().toLong(),
                successCount = payloads.count { it.optBoolean("success", true) },
            )
        }

        val embeddingStats = byType["EMBEDDING_RUN"]?.let { list ->
            val payloads = list.mapNotNull { it.optJSONObject("payload") }
            val totals = payloads.map {
                it.optInt("notes_embedded") +
                it.optInt("interactions_embedded") +
                it.optInt("activities_embedded")
            }
            val durations = payloads.map { it.optLong("duration_ms") }
            EmbeddingStats(
                count = list.size,
                avgItems = if (totals.isEmpty()) 0f else totals.average().toFloat(),
                avgDurationMs = if (durations.isEmpty()) 0 else durations.average().toLong(),
                resultBreakdown = payloads.groupingBy { it.optString("result") }.eachCount(),
            )
        }

        val searchStats = byType["SEARCH_QUERY"]?.let { list ->
            val payloads = list.mapNotNull { it.optJSONObject("payload") }
            val durations = payloads.map { it.optLong("duration_ms") }
            val results = payloads.map { it.optInt("results") }
            SearchStats(
                count = list.size,
                avgDurationMs = if (durations.isEmpty()) 0 else durations.average().toLong(),
                avgResults = if (results.isEmpty()) 0f else results.average().toFloat(),
                zeroVectorCount = payloads.count { it.optBoolean("zero_vector") },
            )
        }

        val backups = byType["BACKUP"]?.mapNotNull { e ->
            val p = e.optJSONObject("payload") ?: return@mapNotNull null
            BackupEntry(
                ts = e.optLong("ts"),
                op = p.optString("op"),
                recordCount = p.optInt("record_count"),
                durationMs = p.optLong("duration_ms"),
                success = p.optBoolean("success", true),
                error = p.optString("error").takeIf { it.isNotEmpty() },
            )
        } ?: emptyList()

        val navTransitions = byType["NAV"]
            ?.mapNotNull { it.optJSONObject("payload") }
            ?.groupingBy { it.optString("from", "—") to it.optString("to") }
            ?.eachCount()
            ?.entries
            ?.sortedByDescending { it.value }
            ?.take(10)
            ?.map { "${it.key.first} → ${it.key.second}" to it.value }
            ?: emptyList()

        val biometricResults = byType["BIOMETRIC"]
            ?.mapNotNull { it.optJSONObject("payload") }
            ?.groupingBy { it.optString("result") }
            ?.eachCount()
            ?: emptyMap()

        val sdf = java.text.SimpleDateFormat("MMM d HH:mm", java.util.Locale.US)
        val flags = buildList {
            byType["LLM_QUERY"]?.forEach { e ->
                val d = e.optJSONObject("payload")?.optLong("duration_ms") ?: 0
                if (d > 10_000) add("${sdf.format(java.util.Date(e.optLong("ts")))}  LLM_QUERY  ${fmtMs(d)}  (>10s)")
            }
            byType["EMBEDDING_RUN"]?.forEach { e ->
                if (e.optJSONObject("payload")?.optString("result") == "retry")
                    add("${sdf.format(java.util.Date(e.optLong("ts")))}  EMBEDDING_RUN  result=retry")
            }
            byType["SEARCH_QUERY"]?.forEach { e ->
                if (e.optJSONObject("payload")?.optBoolean("zero_vector") == true)
                    add("${sdf.format(java.util.Date(e.optLong("ts")))}  SEARCH_QUERY  zero_vector=true")
            }
            byType["BACKUP"]?.forEach { e ->
                if (e.optJSONObject("payload")?.optBoolean("success", true) == false)
                    add("${sdf.format(java.util.Date(e.optLong("ts")))}  BACKUP ${e.optJSONObject("payload")?.optString("op")} FAILED")
            }
        }

        return DebugSummary(
            periodStart = timestamps.minOrNull(),
            periodEnd = timestamps.maxOrNull(),
            totalEvents = events.size,
            queryStats = queryStats,
            summarizeStats = summarizeStats,
            embeddingStats = embeddingStats,
            searchStats = searchStats,
            backups = backups,
            navTransitions = navTransitions,
            biometricResults = biometricResults,
            flags = flags,
        )
    }

}
