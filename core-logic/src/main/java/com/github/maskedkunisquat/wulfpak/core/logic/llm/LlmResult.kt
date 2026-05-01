package com.github.maskedkunisquat.wulfpak.core.logic.llm

sealed class LlmResult {
    data class Token(val text: String) : LlmResult()
    data object Complete : LlmResult()
    data class Error(val cause: Throwable) : LlmResult()
    data class ToolCall(val name: String, val args: Map<String, String>) : LlmResult()
    data class PendingWrite(val id: String, val description: String) : LlmResult()
}
