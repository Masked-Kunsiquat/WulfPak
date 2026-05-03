package com.github.maskedkunisquat.wulfpak.ui.search

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.maskedkunisquat.wulfpak.AppApplication
import com.github.maskedkunisquat.wulfpak.core.logic.llm.LlmResult
import com.github.maskedkunisquat.wulfpak.core.logic.llm.ModelLoadState
import com.github.maskedkunisquat.wulfpak.core.data.entity.SessionMemory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SearchViewModel(app: Application) : AndroidViewModel(app) {

    private val db              = getApplication<AppApplication>().db
    private val llmOrchestrator = getApplication<AppApplication>().llmOrchestrator

    val modelLoadState: StateFlow<ModelLoadState> =
        getApplication<AppApplication>().llmProvider.modelLoadState

    // ── Suggestion chips ──────────────────────────────────────────────────

    private companion object {
        val STATIC_SUGGESTIONS = listOf(
            "Who haven't I talked to recently?",
            "Who did I last contact?",
            "How many contacts do I have?",
            "Who are my family members?",
            "Who do I know from work?",
            "Who are my best friends?",
            "Who am I closest to?",
            "Who goes by a nickname?",
            "What did I do with my contacts recently?",
            "Who did I last call?",
        )
    }

    private var suggestionPool   = STATIC_SUGGESTIONS
    private var suggestionOffset = 0
    var suggestions by mutableStateOf(STATIC_SUGGESTIONS.take(3)); private set

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val persons = try { db.personDao().getAllOnce() } catch (_: Exception) { emptyList() }
            if (persons.isEmpty()) return@launch

            val dynamic = buildList {
                persons.filter { it.lastContactedAt != null }
                    .maxByOrNull { System.currentTimeMillis() - it.lastContactedAt!! }
                    ?.let { add("When did I last talk to ${it.firstName}?") }
                persons.filter { it.isFavorite }.randomOrNull()
                    ?.let { add("How has ${it.firstName} been lately?") }
                persons.maxByOrNull { it.interactionCount }
                    ?.takeIf { it.interactionCount > 0 }
                    ?.let { add("What's new with ${it.firstName}?") }
            }

            if (dynamic.isEmpty()) return@launch
            suggestionPool = (dynamic + STATIC_SUGGESTIONS).distinct()
            suggestions = suggestionPool.take(3)
        }
    }

    fun rotateSuggestions() {
        suggestionOffset = (suggestionOffset + 3) % suggestionPool.size
        suggestions = (suggestionPool.drop(suggestionOffset) + suggestionPool).take(3)
    }

    // ── Chat ──────────────────────────────────────────────────────────────

    var query    by mutableStateOf("")
    var messages by mutableStateOf<List<ChatMessage>>(emptyList()); private set
    private var streamingJob: Job? = null
    private var memorySaved = false

    private fun maybeSaveSessionMemory() {
        if (memorySaved) return
        val toSave = messages
        val hasExchange = toSave.any { it is ChatMessage.User } &&
            toSave.any { it is ChatMessage.Assistant && !(it as ChatMessage.Assistant).isStreaming && (it as ChatMessage.Assistant).text.isNotBlank() }
        if (!hasExchange) return
        memorySaved = true
        // Only include user messages — assistant messages may contain hallucinations
        // that would be baked into session memory and fed back as facts next session.
        val conversationText = buildString {
            toSave.forEach { msg ->
                if (msg is ChatMessage.User) appendLine("User: ${msg.text}")
            }
        }.take(2000)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val text = StringBuilder()
                var hadError = false
                llmOrchestrator.extractSessionMemory(conversationText).collect { result ->
                    when (result) {
                        is LlmResult.Token -> text.append(result.text)
                        is LlmResult.Error -> hadError = true
                        else               -> Unit
                    }
                }
                if (!hadError) {
                    val summary = text.toString().trim()
                    if (summary.isNotEmpty()) {
                        db.sessionMemoryDao().insert(
                            SessionMemory(timestamp = System.currentTimeMillis(), summary = summary)
                        )
                    }
                }
            } catch (_: Exception) { }
        }
    }

    val isQuerying: Boolean
        get() = (messages.lastOrNull() as? ChatMessage.Assistant)?.isStreaming == true

    fun askAi() {
        val q = query.trim()
        if (q.isBlank() || isQuerying) return

        query = ""
        messages = messages + listOf(
            ChatMessage.User(q),
            ChatMessage.Assistant("", isStreaming = true),
        )

        streamingJob = viewModelScope.launch {
            try {
                llmOrchestrator.query(q).collect { result ->
                    val idx  = messages.lastIndex
                    val last = messages.getOrNull(idx) as? ChatMessage.Assistant ?: return@collect
                    messages = when (result) {
                        is LlmResult.Token        -> messages.toMutableList().also { it[idx] = last.copy(text = last.text + result.text) }
                        is LlmResult.Complete     -> messages.toMutableList().also { it[idx] = last.copy(isStreaming = false) }
                        is LlmResult.Error        -> messages.toMutableList().also { it[idx] = last.copy(isStreaming = false) }
                        is LlmResult.ToolCall     -> messages.toMutableList().also {
                            it.add(idx, ChatMessage.ToolCall(result.name, result.args))
                        }
                        is LlmResult.PendingWrite -> messages.toMutableList().also {
                            it.add(idx, ChatMessage.PendingWrite(result.id, result.description))
                        }
                    }
                }
            } catch (_: Exception) {
                val idx  = messages.lastIndex
                val last = messages.getOrNull(idx) as? ChatMessage.Assistant ?: return@launch
                messages = messages.toMutableList().also { it[idx] = last.copy(isStreaming = false) }
            }
        }
    }

    fun confirmPendingWrite(id: String) {
        val idx = messages.indexOfFirst { it is ChatMessage.PendingWrite && it.id == id }
        if (idx < 0) return
        messages = messages.toMutableList().also {
            it[idx] = (it[idx] as ChatMessage.PendingWrite).copy(state = WriteState.CONFIRMED)
        }
        viewModelScope.launch(Dispatchers.IO) {
            llmOrchestrator.executePendingWrite(id)
        }
    }

    fun cancelPendingWrite(id: String) {
        val idx = messages.indexOfFirst { it is ChatMessage.PendingWrite && it.id == id }
        if (idx < 0) return
        messages = messages.toMutableList().also {
            it[idx] = (it[idx] as ChatMessage.PendingWrite).copy(state = WriteState.CANCELLED)
        }
        llmOrchestrator.cancelPendingWrite(id)
    }

    fun toggleToolCall(index: Int) {
        val msg = messages.getOrNull(index) as? ChatMessage.ToolCall ?: return
        messages = messages.toMutableList().also { it[index] = msg.copy(isExpanded = !msg.isExpanded) }
    }

    fun clearConversation() {
        maybeSaveSessionMemory()
        memorySaved = false
        streamingJob?.cancel()
        streamingJob = null
        messages = emptyList()
        query = ""
        viewModelScope.launch(Dispatchers.IO) {
            llmOrchestrator.resetChat()
        }
    }

    override fun onCleared() {
        maybeSaveSessionMemory()
        super.onCleared()
    }
}
