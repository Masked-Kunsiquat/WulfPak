package com.github.maskedkunisquat.wulfpak.ui.person

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.maskedkunisquat.wulfpak.AppApplication
import com.github.maskedkunisquat.wulfpak.AppPrefsKeys
import com.github.maskedkunisquat.wulfpak.appDataStore
import com.github.maskedkunisquat.wulfpak.core.data.dao.PersonConnection
import com.github.maskedkunisquat.wulfpak.core.data.entity.Activity
import com.github.maskedkunisquat.wulfpak.core.data.entity.ContactDetail
import com.github.maskedkunisquat.wulfpak.core.data.entity.Gift
import com.github.maskedkunisquat.wulfpak.core.data.entity.Interaction
import com.github.maskedkunisquat.wulfpak.core.data.entity.LifeEvent
import com.github.maskedkunisquat.wulfpak.core.data.entity.Note
import com.github.maskedkunisquat.wulfpak.core.data.entity.Person
import com.github.maskedkunisquat.wulfpak.core.data.entity.FamilyRelType
import com.github.maskedkunisquat.wulfpak.core.data.entity.PersonRelationship
import com.github.maskedkunisquat.wulfpak.core.data.entity.RelCategory
import com.github.maskedkunisquat.wulfpak.core.data.entity.Task
import com.github.maskedkunisquat.wulfpak.core.logic.closeness.ClosenessCalculator
import com.github.maskedkunisquat.wulfpak.core.logic.family.InferredKin
import com.github.maskedkunisquat.wulfpak.core.logic.llm.LlmResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class PersonDetailViewModel(app: Application) : AndroidViewModel(app) {

    private val db = getApplication<AppApplication>().db
    private val llmOrchestrator = getApplication<AppApplication>().llmOrchestrator
    private val familyEngine = getApplication<AppApplication>().familyInferenceEngine

    var summarizeText by mutableStateOf("")
        private set
    var isSummarizing by mutableStateOf(false)
        private set
    var summaryGeneratedAt by mutableStateOf<Long?>(null)
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

    val connections = _personId.filterNotNull()
        .flatMapLatest { db.personRelationshipDao().getConnectionsForPerson(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<PersonConnection>())

    val inferredKin: StateFlow<List<InferredKin>> = _personId.filterNotNull()
        .flatMapLatest { id ->
            db.personRelationshipDao().getConnectionsForPerson(id).flatMapLatest { conns ->
                if (conns.none { it.category == RelCategory.FAMILY.name }) {
                    flowOf(emptyList())
                } else {
                    flow { emit(familyEngine.inferKinOf(id)) }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var allPersons by mutableStateOf<List<Person>>(emptyList())
        private set

    fun loadAllPersons() {
        viewModelScope.launch {
            val byLast = getApplication<AppApplication>().appDataStore.data
                .first()[AppPrefsKeys.SORT_BY_LAST_NAME] ?: false
            val persons = db.personDao().getAllOnce()
            allPersons = if (byLast)
                persons.sortedWith(compareBy({ it.lastName ?: it.firstName }, { it.firstName }))
            else persons.sortedBy { it.firstName }
        }
    }

    fun addConnection(otherId: UUID, label: String, category: String, relType: String?) {
        val id = _personId.value ?: return
        val iAmPersonA = id.toString() < otherId.toString()
        val (a, b) = if (iAmPersonA) id to otherId else otherId to id
        val storedLabel = if (!iAmPersonA) FAMILY_REVERSE.getOrDefault(label, label) else label
        viewModelScope.launch {
            db.personRelationshipDao().insert(
                PersonRelationship(personAId = a, personBId = b, label = storedLabel, category = category, relType = relType)
            )
        }
    }

    fun removeConnection(otherId: UUID) {
        val id = _personId.value ?: return
        val (a, b) = if (id.toString() < otherId.toString()) id to otherId else otherId to id
        viewModelScope.launch { db.personRelationshipDao().deletePair(a, b) }
    }

    init {
        viewModelScope.launch {
            val p = person.filterNotNull().first()
            if (summarizeText.isEmpty()) {
                summarizeText = p.cachedSummary ?: ""
                summaryGeneratedAt = p.summaryGeneratedAt
            }
        }
    }

    fun load(personId: UUID) { _personId.value = personId }

    fun toggleTaskDone(task: Task) {
        viewModelScope.launch { db.taskDao().update(task.copy(isDone = !task.isDone)) }
    }

    fun deleteActivity(activity: Activity) {
        viewModelScope.launch { db.activityDao().delete(activity) }
    }

    fun deleteInteraction(interaction: Interaction) {
        viewModelScope.launch {
            val affectedIds = db.interactionDao().getParticipantIds(interaction.id)
            db.interactionDao().delete(interaction)
            affectedIds.forEach { pid ->
                db.personDao().onInteractionDeleted(pid)
                val person = db.personDao().getById(pid) ?: return@forEach
                val interactions = db.interactionDao().getForPersonOnce(pid)
                val score = ClosenessCalculator.compute(interactions, ClosenessCalculator.categoryFor(person.relationLabel))
                db.personDao().updateClosenessScore(pid, score)
            }
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
                    is LlmResult.Complete -> {
                        isSummarizing = false
                        val now = System.currentTimeMillis()
                        summaryGeneratedAt = now
                        db.personDao().updateSummary(id, summarizeText, now)
                    }
                    is LlmResult.Error -> isSummarizing = false
                    is LlmResult.ToolCall -> Unit
                    is LlmResult.PendingWrite -> Unit
                }
            }
        }
    }

    fun addContactDetail(detail: ContactDetail) {
        viewModelScope.launch { db.contactDetailDao().insert(detail) }
    }

    fun updateContactDetail(detail: ContactDetail) {
        viewModelScope.launch { db.contactDetailDao().update(detail) }
    }

    fun deleteContactDetail(detail: ContactDetail) {
        viewModelScope.launch { db.contactDetailDao().delete(detail) }
    }

    fun deletePerson(onDone: () -> Unit) {
        viewModelScope.launch {
            person.value?.let { db.personDao().delete(it) }
            onDone()
        }
    }

    companion object {
        private val FAMILY_REVERSE: Map<String, String> = FamilyRelType.entries
            .flatMap { t -> listOf(t.displayLabel to t.reverseLabel, t.reverseLabel to t.displayLabel) }
            .toMap()
    }
}
