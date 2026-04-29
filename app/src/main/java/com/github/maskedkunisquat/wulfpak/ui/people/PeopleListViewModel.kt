package com.github.maskedkunisquat.wulfpak.ui.people

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.maskedkunisquat.wulfpak.AppApplication
import com.github.maskedkunisquat.wulfpak.core.data.entity.Person
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class PeopleListViewModel(app: Application) : AndroidViewModel(app) {

    private val personDao = getApplication<AppApplication>().db.personDao()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val people = _searchQuery
        .flatMapLatest { q ->
            if (q.isBlank()) personDao.getAll() else personDao.search(q)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedIds = MutableStateFlow(emptySet<UUID>())
    val selectedIds = _selectedIds.asStateFlow()

    val isMultiSelectMode: Boolean get() = _selectedIds.value.isNotEmpty()

    fun setSearchQuery(q: String) { _searchQuery.value = q }

    fun toggleFavorite(person: Person) {
        viewModelScope.launch { personDao.update(person.copy(isFavorite = !person.isFavorite)) }
    }

    fun delete(person: Person) {
        viewModelScope.launch { personDao.delete(person) }
    }

    fun enterMultiSelect(person: Person) {
        _selectedIds.value = setOf(person.id)
    }

    fun toggleSelection(person: Person) {
        val current = _selectedIds.value
        _selectedIds.value = if (person.id in current) current - person.id else current + person.id
    }

    fun clearSelection() { _selectedIds.value = emptySet() }

    fun bulkDelete() {
        val ids = _selectedIds.value
        viewModelScope.launch {
            ids.forEach { id -> personDao.getById(id)?.let { personDao.delete(it) } }
            _selectedIds.value = emptySet()
        }
    }

    fun bulkSetRelation(relation: String) {
        val ids = _selectedIds.value
        viewModelScope.launch {
            ids.forEach { id -> personDao.getById(id)?.let { personDao.update(it.copy(relationLabel = relation)) } }
            _selectedIds.value = emptySet()
        }
    }
}
