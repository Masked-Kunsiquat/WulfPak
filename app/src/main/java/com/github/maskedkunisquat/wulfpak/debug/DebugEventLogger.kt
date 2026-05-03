package com.github.maskedkunisquat.wulfpak.debug

import android.content.Context
import android.net.Uri
import com.github.maskedkunisquat.wulfpak.core.logic.debug.DebugEvent
import com.github.maskedkunisquat.wulfpak.core.logic.debug.DebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class DebugEventLogger(context: Context) : DebugLogger {

    private val logFile = File(context.filesDir, "debug_events.ndjson")
    private val bakFile = File(context.filesDir, "debug_events.ndjson.bak")

    @Volatile private var captureEnabled = false

    fun updateCaptureEnabled(enabled: Boolean) { captureEnabled = enabled }

    override fun log(event: DebugEvent) {
        if (!captureEnabled) return
        val line = event.toJsonLine()
        synchronized(this) {
            if (logFile.length() > MAX_FILE_BYTES) logFile.renameTo(bakFile)
            logFile.appendText(line + "\n")
        }
    }

    fun totalCount(): Int = synchronized(this) { lineCount(bakFile) + lineCount(logFile) }

    suspend fun exportToUri(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val events = readAllJsonObjects()
        val root = JSONObject().apply {
            put("exported_at", System.currentTimeMillis())
            put("event_count", events.size)
            val arr = JSONArray()
            events.forEach { arr.put(it) }
            put("events", arr)
        }
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.writer().use { it.write(root.toString(2)) }
        }
    }

    fun generateTextSummary(): String {
        val events = readAllJsonObjects()
        if (events.isEmpty()) return "No events captured."

        val byType = events.groupBy { it.optString("type") }
        val timestamps = events.mapNotNull { runCatching { it.getLong("ts") }.getOrNull() }
        val first = timestamps.minOrNull() ?: return "No events."
        val last  = timestamps.maxOrNull() ?: first
        val spanH = (last - first) / 3_600_000.0

        return buildString {
            appendLine("=== WulfPak Debug Digest ===")
            appendLine("Period  : ${fmtTs(first)} → ${fmtTs(last)}  (${"%.1f".format(spanH)}h)")
            appendLine("Events  : ${events.size} total")

            byType["LLM_QUERY"]?.let { list ->
                val payloads = list.mapNotNull { it.optJSONObject("payload") }
                val durations = payloads.map { it.getLong("duration_ms") }
                val toolCounts = payloads.map { it.getInt("tool_call_count") }
                val allTools = payloads.flatMap { p ->
                    val arr = p.optJSONArray("tools_used") ?: return@flatMap emptyList()
                    (0 until arr.length()).map { arr.getString(it) }
                }
                val toolFreq = allTools.groupingBy { it }.eachCount().entries
                    .sortedByDescending { it.value }
                appendLine("\n── LLM Queries (${list.size}) ──────────────────────────────────────")
                appendLine("  Duration   avg ${fmtMs(durations.average().toLong())}  " +
                    "max ${fmtMs(durations.max())}")
                appendLine("  Tools/query avg ${"%.1f".format(toolCounts.average())}  max ${toolCounts.max()}")
                if (allTools.isNotEmpty()) {
                    appendLine("  Tool breakdown:")
                    toolFreq.take(8).forEach { (tool, cnt) ->
                        val pct = (100.0 * cnt / allTools.size).toInt()
                        appendLine("    ${tool.padEnd(32)} $cnt  ($pct%)")
                    }
                }
            }

            byType["LLM_SUMMARIZE"]?.let { list ->
                val payloads = list.mapNotNull { it.optJSONObject("payload") }
                val ok = payloads.count { it.optBoolean("success", true) }
                val durations = payloads.map { it.getLong("duration_ms") }
                appendLine("\n── LLM Summarize (${list.size}) ──────────────────────────────────")
                appendLine("  avg ${fmtMs(durations.average().toLong())}  success $ok/${list.size}")
            }

            byType["EMBEDDING_RUN"]?.let { list ->
                val payloads = list.mapNotNull { it.optJSONObject("payload") }
                val totals = payloads.map {
                    it.getInt("notes_embedded") +
                    it.getInt("interactions_embedded") +
                    it.getInt("activities_embedded")
                }
                val durations = payloads.map { it.getLong("duration_ms") }
                val results = payloads.groupingBy { it.optString("result") }.eachCount()
                appendLine("\n── Embedding Runs (${list.size}) ──────────────────────────────────")
                appendLine("  avg ${"%.1f".format(totals.average())} items  " +
                    "avg ${fmtMs(durations.average().toLong())}")
                appendLine("  $results")
            }

            byType["SEARCH_QUERY"]?.let { list ->
                val payloads = list.mapNotNull { it.optJSONObject("payload") }
                val durations = payloads.map { it.getLong("duration_ms") }
                val results = payloads.map { it.getInt("results") }
                val zeros = payloads.count { it.optBoolean("zero_vector") }
                appendLine("\n── Search Queries (${list.size}) ──────────────────────────────────")
                appendLine("  avg ${fmtMs(durations.average().toLong())}  " +
                    "avg results ${"%.1f".format(results.average())}  zero-vector $zeros")
            }

            byType["BACKUP"]?.let { list ->
                appendLine("\n── Backup (${list.size}) ──────────────────────────────────────────")
                list.forEach { e ->
                    val p = e.optJSONObject("payload") ?: return@forEach
                    val ok = if (p.optBoolean("success", true)) "ok" else "FAIL"
                    appendLine("  ${p.optString("op").padEnd(8)}  " +
                        "${p.optInt("record_count")} records  " +
                        "${fmtMs(p.optLong("duration_ms"))}  $ok")
                }
            }

            byType["NAV"]?.let { list ->
                val transitions = list
                    .mapNotNull { it.optJSONObject("payload") }
                    .groupingBy { it.optString("from", "—") to it.optString("to") }
                    .eachCount()
                    .entries
                    .sortedByDescending { it.value }
                appendLine("\n── Navigation (${list.size}) ──────────────────────────────────────")
                transitions.take(8).forEach { (pair, cnt) ->
                    appendLine("  ${pair.first.padEnd(30)} → ${pair.second.padEnd(25)} $cnt")
                }
            }

            byType["BIOMETRIC"]?.let { list ->
                val results = list.mapNotNull { it.optJSONObject("payload") }
                    .groupingBy { it.optString("result") }.eachCount()
                appendLine("\n── Biometric (${list.size}) ──────────────────────────────────────")
                appendLine("  $results")
            }

            val flags = buildList {
                byType["LLM_QUERY"]?.forEach { e ->
                    val d = e.optJSONObject("payload")?.optLong("duration_ms") ?: 0
                    if (d > 10_000) add("  ⚠ ${fmtTs(e.optLong("ts"))}  LLM_QUERY  ${fmtMs(d)}  (>10s)")
                }
                byType["EMBEDDING_RUN"]?.forEach { e ->
                    if (e.optJSONObject("payload")?.optString("result") == "retry")
                        add("  ⚠ ${fmtTs(e.optLong("ts"))}  EMBEDDING_RUN  result=retry")
                }
                byType["SEARCH_QUERY"]?.forEach { e ->
                    if (e.optJSONObject("payload")?.optBoolean("zero_vector") == true)
                        add("  ⚠ ${fmtTs(e.optLong("ts"))}  SEARCH_QUERY  zero_vector=true")
                }
                byType["BACKUP"]?.forEach { e ->
                    if (e.optJSONObject("payload")?.optBoolean("success", true) == false)
                        add("  ⚠ ${fmtTs(e.optLong("ts"))}  BACKUP  ${e.optJSONObject("payload")?.optString("op")} FAILED")
                }
            }
            if (flags.isNotEmpty()) {
                appendLine("\n── Flags ──────────────────────────────────────────────────────────")
                flags.forEach { appendLine(it) }
            }
        }
    }

    fun clear() = synchronized(this) { logFile.delete(); bakFile.delete() }

    fun readAllJsonObjects(): List<JSONObject> {
        val result = mutableListOf<JSONObject>()
        listOf(bakFile, logFile).forEach { file ->
            if (!file.exists()) return@forEach
            file.bufferedReader().forEachLine { line ->
                if (line.isNotBlank()) runCatching { result.add(JSONObject(line)) }
            }
        }
        return result
    }

    private fun lineCount(file: File): Int {
        if (!file.exists()) return 0
        return file.bufferedReader().lineSequence().count { it.isNotBlank() }
    }

    private fun fmtTs(ms: Long): String {
        val sdf = java.text.SimpleDateFormat("MMM d HH:mm", java.util.Locale.US)
        return sdf.format(java.util.Date(ms))
    }

    private fun fmtMs(ms: Long): String = when {
        ms < 1_000  -> "${ms}ms"
        ms < 60_000 -> "${"%.1f".format(ms / 1000.0)}s"
        else        -> "${"%.1f".format(ms / 60_000.0)}m"
    }

    private fun DebugEvent.toJsonLine(): String = JSONObject().apply {
        put("id", UUID.randomUUID().toString())
        put("ts", ts)
        sessionId?.let { put("session", it) }
        put("v", 1)
        when (val e = this@toJsonLine) {
            is DebugEvent.LlmQuery -> {
                put("type", "LLM_QUERY")
                put("payload", JSONObject().apply {
                    put("duration_ms", e.durationMs)
                    put("tool_call_count", e.toolCallCount)
                    put("tools_used", JSONArray(e.toolsUsed))
                    e.error?.let { put("error", it) }
                })
            }
            is DebugEvent.ToolCall -> {
                put("type", "TOOL_CALL")
                put("payload", JSONObject().apply {
                    put("tool", e.tool)
                    put("arg_keys", JSONArray(e.argKeys))
                    put("success", e.success)
                })
            }
            is DebugEvent.LlmSummarize -> {
                put("type", "LLM_SUMMARIZE")
                put("payload", JSONObject().apply {
                    put("subject", e.subject)
                    put("duration_ms", e.durationMs)
                    put("success", e.success)
                })
            }
            is DebugEvent.EmbeddingRun -> {
                put("type", "EMBEDDING_RUN")
                put("payload", JSONObject().apply {
                    put("notes_embedded", e.notesEmbedded)
                    put("interactions_embedded", e.interactionsEmbedded)
                    put("activities_embedded", e.activitiesEmbedded)
                    put("failed", e.failed)
                    put("duration_ms", e.durationMs)
                    put("result", e.result)
                })
            }
            is DebugEvent.SearchQuery -> {
                put("type", "SEARCH_QUERY")
                put("payload", JSONObject().apply {
                    put("query_len", e.queryLen)
                    put("candidates", e.candidates)
                    put("results", e.results)
                    put("duration_ms", e.durationMs)
                    put("zero_vector", e.zeroVector)
                })
            }
            is DebugEvent.Backup -> {
                put("type", "BACKUP")
                put("payload", JSONObject().apply {
                    put("op", e.op)
                    put("record_count", e.recordCount)
                    put("duration_ms", e.durationMs)
                    put("success", e.success)
                    e.error?.let { put("error", it) }
                })
            }
            is DebugEvent.Nav -> {
                put("type", "NAV")
                put("payload", JSONObject().apply {
                    put("to", e.to)
                    e.from?.let { put("from", it) }
                })
            }
            is DebugEvent.Biometric -> {
                put("type", "BIOMETRIC")
                put("payload", JSONObject().apply { put("result", e.result) })
            }
        }
    }.toString()

    companion object {
        private const val MAX_FILE_BYTES = 5L * 1024 * 1024
    }
}
