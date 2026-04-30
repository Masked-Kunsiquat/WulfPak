package com.github.maskedkunisquat.wulfpak.ui.feed

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.maskedkunisquat.wulfpak.AppApplication
import com.github.maskedkunisquat.wulfpak.core.data.entity.Person
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityDetailViewModel(app: Application) : AndroidViewModel(app) {

    private val db = getApplication<AppApplication>().db

    private val _activityId = MutableStateFlow<UUID?>(null)

    val activity = _activityId
        .filterNotNull()
        .flatMapLatest { db.activityDao().observe(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val participants = _activityId
        .filterNotNull()
        .flatMapLatest { db.activityDao().getParticipants(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<Person>())

    fun load(id: UUID) { _activityId.value = id }
}
