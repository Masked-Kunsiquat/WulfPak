package com.github.maskedkunisquat.wulfpak.ui.search

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.maskedkunisquat.wulfpak.AppApplication
import com.github.maskedkunisquat.wulfpak.core.logic.llm.LlmResult
import com.github.maskedkunisquat.wulfpak.core.logic.search.SearchHit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SearchViewModel(app: Application) : AndroidViewModel(app) {

    private val searchRepository = getApplication<AppApplication>().searchRepository
    private val llmOrchestrator  = getApplication<AppApplication>().llmOrchestrator

    // ── Ask AI suggestion chips ───────────────────────────────────────────

    private val SUGGESTION_POOL = listOf(
        "Who is my mother?",
        "Who haven't I talked to recently?",
        "Who did I last contact?",
        "How many contacts do I have?",
        "Who are my family members?",
        "Who do I know from work?",
        "Who are my best friends?",
        "Who am I closest to?",
        "Who goes by a nickname?",
        "What did I do with my contacts recently?",
        "What notes do I have about my contacts?",
        "Who did I last call?",
    )

    private var suggestionOffset = 0
    var suggestions by mutableStateOf(SUGGESTION_POOL.take(3)) ; private set

    fun rotateSuggestions() {
        suggestionOffset = (suggestionOffset + 3) % SUGGESTION_POOL.size
        suggestions = (SUGGESTION_POOL.drop(suggestionOffset) + SUGGESTION_POOL).take(3)
    }

    // ─────────────────────────────────────────────────────────────────────

    var query by mutableStateOf("")
    var isAskAiMode by mutableStateOf(false)

    var results by mutableStateOf<List<SearchHit>>(emptyList())
        private set
    var isSearching by mutableStateOf(false)
        private set

    var nlResponse by mutableStateOf("")
        private set
    var isNlQuerying by mutableStateOf(false)
        private set

    fun search() {
        val q = query.trim()
        if (q.isBlank() || isSearching) return
        isSearching = true
        viewModelScope.launch(Dispatchers.IO) {
            results = try { searchRepository.search(q) } catch (_: Exception) { emptyList() }
            isSearching = false
        }
    }

    fun askAi() {
        val q = query.trim()
        if (q.isBlank() || isNlQuerying) return
        isNlQuerying = true
        nlResponse = ""
        viewModelScope.launch {
            try {
                llmOrchestrator.query(q).collect { result ->
                    when (result) {
                        is LlmResult.Token -> nlResponse += result.text
                        is LlmResult.Complete, is LlmResult.Error -> isNlQuerying = false
                    }
                }
            } catch (_: Exception) {
                isNlQuerying = false
            }
        }
    }

    fun clearResults() {
        query = ""
        results = emptyList()
        nlResponse = ""
    }
}
