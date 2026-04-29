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
}
