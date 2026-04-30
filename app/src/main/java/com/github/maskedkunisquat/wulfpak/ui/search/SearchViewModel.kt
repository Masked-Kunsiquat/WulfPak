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
import com.github.maskedkunisquat.wulfpak.core.logic.search.SearchHit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SearchViewModel(app: Application) : AndroidViewModel(app) {

    private val db               = getApplication<AppApplication>().db
    private val searchRepository = getApplication<AppApplication>().searchRepository
    private val llmOrchestrator  = getApplication<AppApplication>().llmOrchestrator

    val modelLoadState: StateFlow<ModelLoadState> =
        getApplication<AppApplication>().llmProvider.modelLoadState

    // ── Ask AI suggestion chips ───────────────────────────────────────────

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

    private var suggestionPool = STATIC_SUGGESTIONS
    private var suggestionOffset = 0
    var suggestions by mutableStateOf(STATIC_SUGGESTIONS.take(3)); private set

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val persons = try { db.personDao().getAllOnce() } catch (_: Exception) { emptyList() }
            if (persons.isEmpty()) return@launch

            val dynamic = buildList {
                // Most overdue contact
                persons.filter { it.lastContactedAt != null }
                    .maxByOrNull { System.currentTimeMillis() - it.lastContactedAt!! }
                    ?.let { add("When did I last talk to ${it.firstName}?") }

                // A starred contact
                persons.filter { it.isFavorite }.randomOrNull()
                    ?.let { add("How has ${it.firstName} been lately?") }

                // Most interacted contact
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

    // ── Shared ────────────────────────────────────────────────────────────

    var query by mutableStateOf("")
    var isAskAiMode by mutableStateOf(false)

    // ── Semantic search ───────────────────────────────────────────────────

    var results by mutableStateOf<List<SearchHit>>(emptyList()); private set
    var isSearching by mutableStateOf(false); private set

    fun search() {
        val q = query.trim()
        if (q.isBlank() || isSearching) return
        isSearching = true
        viewModelScope.launch(Dispatchers.IO) {
            results = try { searchRepository.search(q) } catch (_: Exception) { emptyList() }
            isSearching = false
        }
    }

    fun clearResults() { results = emptyList() }

    // ── Ask AI conversation ───────────────────────────────────────────────

    var messages by mutableStateOf<List<ChatMessage>>(emptyList()); private set
    private var streamingJob: Job? = null

    val isNlQuerying: Boolean
        get() = (messages.lastOrNull() as? ChatMessage.Assistant)?.isStreaming == true

    fun askAi() {
        val q = query.trim()
        if (q.isBlank() || isNlQuerying) return

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
                        is LlmResult.Token    -> messages.toMutableList().also { it[idx] = last.copy(text = last.text + result.text) }
                        is LlmResult.Complete -> messages.toMutableList().also { it[idx] = last.copy(isStreaming = false) }
                        is LlmResult.Error    -> messages.toMutableList().also { it[idx] = last.copy(isStreaming = false) }
                    }
                }
            } catch (_: Exception) {
                val idx  = messages.lastIndex
                val last = messages.getOrNull(idx) as? ChatMessage.Assistant ?: return@launch
                messages = messages.toMutableList().also { it[idx] = last.copy(isStreaming = false) }
            }
        }
    }

    fun clearConversation() {
        streamingJob?.cancel()
        streamingJob = null
        messages = emptyList()
        query = ""
        viewModelScope.launch(Dispatchers.IO) {
            llmOrchestrator.resetChat()
        }
    }
}
