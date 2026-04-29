package com.github.maskedkunisquat.wulfpak.ui.search

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.maskedkunisquat.wulfpak.AppApplication
import com.github.maskedkunisquat.wulfpak.core.logic.search.SearchHit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SearchViewModel(app: Application) : AndroidViewModel(app) {

    private val searchRepository = getApplication<AppApplication>().searchRepository

    var query   by mutableStateOf("")
    var results by mutableStateOf<List<SearchHit>>(emptyList())
        private set
    var isSearching by mutableStateOf(false)
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

    fun clearResults() {
        query = ""
        results = emptyList()
    }
}
