package com.yourapp.llm

import kotlinx.coroutines.flow.Flow

/**
 * Contract for an LLM inference backend.
 *
 * For this app only one implementation is expected: [LocalFallbackProvider] (Gemma 3 1B via
 * LiteRT-LM, fully on-device). The interface exists to allow test doubles and future expansion.
 */
interface LlmProvider {
    /** Stable identifier used in UI labels. */
    val id: String

    /** Returns true if this provider can currently handle requests. */
    suspend fun isAvailable(): Boolean

    /**
     * Streams inference results for the given [prompt].
     *
     * Emits [LlmResult.Token] for each generated token, then [LlmResult.Complete]
     * or [LlmResult.Error]. The flow is cold — collection starts inference.
     *
     * @param prompt The user message — plain text, no chat-format tokens.
     * @param systemInstruction Optional system-level instruction.
     */
    fun process(prompt: String, systemInstruction: String? = null): Flow<LlmResult>
}
