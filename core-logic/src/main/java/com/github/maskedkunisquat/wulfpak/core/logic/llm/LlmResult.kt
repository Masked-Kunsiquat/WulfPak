package com.github.maskedkunisquat.wulfpak.core.logic.llm

sealed class LlmResult {
    data class Token(val text: String) : LlmResult()
    data object Complete : LlmResult()
    data class Error(val cause: Throwable) : LlmResult()
}
