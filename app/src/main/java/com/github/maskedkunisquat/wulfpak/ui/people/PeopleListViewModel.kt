package com.github.maskedkunisquat.wulfpak.ui.people

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.maskedkunisquat.wulfpak.AppApplication
import com.github.maskedkunisquat.wulfpak.AppPrefsKeys
import com.github.maskedkunisquat.wulfpak.appDataStore
import com.github.maskedkunisquat.wulfpak.core.data.entity.Person
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class PeopleListViewModel(app: Application) : AndroidViewModel(app) {

    private val personDao = getApplication<AppApplication>().db.personDao()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val sortByLastName = getApplication<AppApplication>().appDataStore.data
        .map { it[AppPrefsKeys.SORT_BY_LAST_NAME] ?: false }

    @OptIn(ExperimentalCoroutinesApi::class)
    val people = combine(
        _searchQuery.flatMapLatest { q ->
            if (q.isBlank()) personDao.getAll() else personDao.search(q)
        },
        sortByLastName,
    ) { persons, byLast ->
        val filtered = persons.filter { !it.isMe }
        if (byLast) filtered.sortedWith(compareBy({ it.lastName ?: it.firstName }, { it.firstName }))
        else filtered.sortedBy { it.firstName }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
