package com.github.maskedkunisquat.wulfpak.ui.person

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.maskedkunisquat.wulfpak.AppApplication
import com.github.maskedkunisquat.wulfpak.core.data.entity.Activity
import com.github.maskedkunisquat.wulfpak.core.data.entity.ContactDetail
import com.github.maskedkunisquat.wulfpak.core.data.entity.Gift
import com.github.maskedkunisquat.wulfpak.core.data.entity.Interaction
import com.github.maskedkunisquat.wulfpak.core.data.entity.LifeEvent
import com.github.maskedkunisquat.wulfpak.core.data.entity.Note
import com.github.maskedkunisquat.wulfpak.core.data.entity.Person
import com.github.maskedkunisquat.wulfpak.core.data.entity.Task
import com.github.maskedkunisquat.wulfpak.core.logic.llm.LlmResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class PersonDetailViewModel(app: Application) : AndroidViewModel(app) {

    private val db = getApplication<AppApplication>().db
    private val llmOrchestrator = getApplication<AppApplication>().llmOrchestrator

    var summarizeText by mutableStateOf("")
        private set
    var isSummarizing by mutableStateOf(false)
        private set

    private val _personId = MutableStateFlow<UUID?>(null)

    val person = _personId.filterNotNull()
        .flatMapLatest { db.personDao().observe(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val interactions = _personId.filterNotNull()
        .flatMapLatest { db.interactionDao().getForPerson(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<Interaction>())

    val notes = _personId.filterNotNull()
        .flatMapLatest { db.noteDao().getForPerson(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<Note>())

    val lifeEvents = _personId.filterNotNull()
        .flatMapLatest { db.lifeEventDao().getForPerson(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<LifeEvent>())

    val gifts = _personId.filterNotNull()
        .flatMapLatest { db.giftDao().getForPerson(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<Gift>())

    val tasks = _personId.filterNotNull()
        .flatMapLatest { db.taskDao().getForPerson(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<Task>())

    val activities = _personId.filterNotNull()
        .flatMapLatest { db.activityDao().getForPerson(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<Activity>())

    val contactDetails = _personId.filterNotNull()
        .flatMapLatest { db.contactDetailDao().getForPerson(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<ContactDetail>())

    fun load(personId: UUID) { _personId.value = personId }

    fun toggleTaskDone(task: Task) {
        viewModelScope.launch { db.taskDao().update(task.copy(isDone = !task.isDone)) }
    }

    fun deleteActivity(activity: Activity) {
        viewModelScope.launch { db.activityDao().delete(activity) }
    }

    fun deleteInteraction(interaction: Interaction) {
        viewModelScope.launch {
            db.interactionDao().delete(interaction)
            _personId.value?.let { db.personDao().onInteractionDeleted(it) }
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch { db.noteDao().delete(note) }
    }

    fun deleteLifeEvent(lifeEvent: LifeEvent) {
        viewModelScope.launch { db.lifeEventDao().delete(lifeEvent) }
    }

    fun deleteGift(gift: Gift) {
        viewModelScope.launch { db.giftDao().delete(gift) }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch { db.taskDao().delete(task) }
    }

    fun summarize() {
        val id = _personId.value ?: return
        if (isSummarizing) return
        isSummarizing = true
        summarizeText = ""
        viewModelScope.launch {
            llmOrchestrator.summarize(id).collect { result ->
                when (result) {
                    is LlmResult.Token -> summarizeText += result.text
                    is LlmResult.Complete, is LlmResult.Error -> isSummarizing = false
                }
            }
        }
    }

    fun deletePerson(onDone: () -> Unit) {
        viewModelScope.launch {
            person.value?.let { db.personDao().delete(it) }
            onDone()
        }
    }
}
