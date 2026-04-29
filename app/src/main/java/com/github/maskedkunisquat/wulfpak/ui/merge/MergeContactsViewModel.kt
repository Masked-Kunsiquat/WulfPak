package com.github.maskedkunisquat.wulfpak.ui.merge

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.maskedkunisquat.wulfpak.AppApplication
import com.github.maskedkunisquat.wulfpak.core.data.entity.Person
import com.github.maskedkunisquat.wulfpak.merge.MergeRepository
import kotlinx.coroutines.launch
import java.util.UUID

class MergeContactsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = MergeRepository(getApplication<AppApplication>().db)

    data class DuplicatePair(val keep: Person, val discard: Person)

    var pairs by mutableStateOf<List<DuplicatePair>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var isMerging by mutableStateOf(false)
        private set

    init { refresh() }

    private fun refresh() {
        viewModelScope.launch {
            isLoading = true
            pairs = repo.findDuplicates().map { (keep, discard) -> DuplicatePair(keep, discard) }
            isLoading = false
        }
    }

    fun merge(keepId: UUID, discardId: UUID) {
        if (isMerging) return
        isMerging = true
        viewModelScope.launch {
            repo.merge(keepId, discardId)
            isMerging = false
            refresh()
        }
    }
}
