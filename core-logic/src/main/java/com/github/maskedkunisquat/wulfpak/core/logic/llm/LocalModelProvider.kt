package com.github.maskedkunisquat.wulfpak.core.logic.llm

import kotlinx.coroutines.flow.StateFlow

interface LocalModelProvider : LlmProvider {
    val modelLoadState: StateFlow<ModelLoadState>
    val loadedModelName: StateFlow<String?>

    fun initialize()
    fun downloadModel()
}
