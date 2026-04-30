package com.github.maskedkunisquat.wulfpak.ui.search

sealed class ChatMessage {
    data class User(val text: String) : ChatMessage()
    data class Assistant(val text: String, val isStreaming: Boolean = false) : ChatMessage()
}
