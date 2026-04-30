package com.github.maskedkunisquat.wulfpak.core.logic.llm

import kotlinx.coroutines.flow.Flow

interface LlmProvider {
    val id: String

    suspend fun isAvailable(): Boolean

    /**
     * Streams inference results for [prompt].
     * Emits [LlmResult.Token] per token, then [LlmResult.Complete] or [LlmResult.Error].
     */
    fun process(prompt: String, systemInstruction: String? = null): Flow<LlmResult>

    /**
     * Sends [prompt] to a persistent Conversation, creating it with [systemInstruction]
     * and [tools] on the first call. Subsequent calls reuse the same Conversation —
     * [systemInstruction] and [tools] are ignored once the Conversation exists.
     */
    fun chatSend(
        prompt: String,
        systemInstruction: String? = null,
        tools: List<Any> = emptyList(),
    ): Flow<LlmResult>

    /** Close and discard the persistent Conversation. */
    fun resetChat()
}
