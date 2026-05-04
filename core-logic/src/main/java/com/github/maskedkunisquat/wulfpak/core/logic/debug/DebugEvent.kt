package com.github.maskedkunisquat.wulfpak.core.logic.debug

sealed class DebugEvent {
    abstract val ts: Long
    abstract val sessionId: String?

    data class LlmQuery(
        override val ts: Long = System.currentTimeMillis(),
        override val sessionId: String? = null,
        val durationMs: Long,
        val toolCallCount: Int,
        val toolsUsed: List<String>,
        val error: String? = null,
    ) : DebugEvent()

    data class ToolCall(
        override val ts: Long = System.currentTimeMillis(),
        override val sessionId: String? = null,
        val tool: String,
        val argKeys: List<String>,
        val success: Boolean,
    ) : DebugEvent()

    data class LlmSummarize(
        override val ts: Long = System.currentTimeMillis(),
        override val sessionId: String? = null,
        val subject: String,
        val durationMs: Long,
        val success: Boolean,
    ) : DebugEvent()

    data class EmbeddingRun(
        override val ts: Long = System.currentTimeMillis(),
        override val sessionId: String? = null,
        val notesEmbedded: Int,
        val interactionsEmbedded: Int,
        val activitiesEmbedded: Int,
        val failed: Boolean,
        val durationMs: Long,
        val result: String,
    ) : DebugEvent()

    data class SearchQuery(
        override val ts: Long = System.currentTimeMillis(),
        override val sessionId: String? = null,
        val queryLen: Int,
        val candidates: Int,
        val results: Int,
        val durationMs: Long,
        val zeroVector: Boolean,
    ) : DebugEvent()

    data class Backup(
        override val ts: Long = System.currentTimeMillis(),
        override val sessionId: String? = null,
        val op: String,
        val recordCount: Int,
        val durationMs: Long,
        val success: Boolean,
        val error: String? = null,
    ) : DebugEvent()

    data class Nav(
        override val ts: Long = System.currentTimeMillis(),
        override val sessionId: String? = null,
        val to: String,
        val from: String?,
    ) : DebugEvent()

    data class Biometric(
        override val ts: Long = System.currentTimeMillis(),
        override val sessionId: String? = null,
        val result: String,
    ) : DebugEvent()

    data class CallLogImport(
        override val ts: Long = System.currentTimeMillis(),
        override val sessionId: String? = null,
        val stubsFound: Int,
        val stubsAdded: Int,
        val durationMs: Long,
    ) : DebugEvent()

    data class PendingCallAction(
        override val ts: Long = System.currentTimeMillis(),
        override val sessionId: String? = null,
        val action: String,   // "CONFIRM" | "SKIP"
        val callType: String, // "INCOMING" | "OUTGOING" | "MISSED"
    ) : DebugEvent()
}
