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

    fun setSearchQuery(q: String) { _searchQuery.value = q }

    fun toggleFavorite(person: Person) {
        viewModelScope.launch { personDao.update(person.copy(isFavorite = !person.isFavorite)) }
    }

    fun delete(person: Person) {
        viewModelScope.launch { personDao.delete(person) }
    }
}
