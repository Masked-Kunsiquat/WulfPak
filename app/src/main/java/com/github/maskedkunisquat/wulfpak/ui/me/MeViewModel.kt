package com.github.maskedkunisquat.wulfpak.ui.me

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.maskedkunisquat.wulfpak.AppApplication
import com.github.maskedkunisquat.wulfpak.core.data.entity.LifeEvent
import com.github.maskedkunisquat.wulfpak.core.data.entity.Person
import com.github.maskedkunisquat.wulfpak.core.data.entity.Task
import com.github.maskedkunisquat.wulfpak.core.logic.llm.LlmResult
import com.github.maskedkunisquat.wulfpak.ui.feed.FeedItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class MeViewModel(app: Application) : AndroidViewModel(app) {

    private val db = getApplication<AppApplication>().db
    private val llm = getApplication<AppApplication>().llmOrchestrator

    var meSummaryText by mutableStateOf("")
        private set
    var isSummarizing by mutableStateOf(false)
        private set
    var summaryGeneratedAt by mutableStateOf<Long?>(null)
        private set

    data class TaskWithPerson(val task: Task, val person: Person?)

    val me = db.personDao().observeMe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val meLifeEvents = me.filterNotNull()
        .flatMapLatest { db.lifeEventDao().getForPerson(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<LifeEvent>())

    val totalContacts = db.personDao().getAll()
        .map { it.count { p -> !p.isMe } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val interactionsThisMonth = db.interactionDao().getAll()
        .map { interactions ->
            val monthStart = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            interactions.count { it.timestamp >= monthStart }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val feed = combine(
        db.interactionDao().getAll(),
        db.activityDao().getAll(),
    ) { interactions, activities ->
        buildList {
            interactions.forEach { add(FeedItem.InteractionItem(it)) }
            activities.forEach { add(FeedItem.ActivityItem(it)) }
        }.sortedByDescending { it.timestamp }.take(50)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val personsById = db.personDao().getAll()
        .map { it.associateBy { p -> p.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap<UUID, Person>())

    val rankedContacts = db.personDao().getAll()
        .map { persons ->
            persons.filter { !it.isMe }
                .sortedWith(compareBy(nullsLast(reverseOrder<Float>())) { it.closenessScore })
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<Person>())

    val lapsingContacts = db.personDao().getAll()
        .map { persons ->
            val cutoff = System.currentTimeMillis() - 60L * 24 * 60 * 60 * 1000
            persons.filter { p -> !p.isMe && p.lastContactedAt.let { lca -> lca == null || lca < cutoff } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<Person>())

    val allOpenTasks = combine(
        db.taskDao().getPending(),
        db.personDao().getAll(),
    ) { tasks, persons ->
        val personMap = persons.associateBy { it.id }
        tasks.map { TaskWithPerson(it, it.personId?.let { pid -> personMap[pid] }) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val p = db.personDao().getMe() ?: return@launch
            if (meSummaryText.isEmpty()) {
                meSummaryText = p.cachedSummary ?: ""
                summaryGeneratedAt = p.summaryGeneratedAt
            }
        }
    }

    fun summarizeMe() {
        if (isSummarizing) return
        isSummarizing = true
        meSummaryText = ""
        viewModelScope.launch {
            llm.summarizeMe().collect { result ->
                when (result) {
                    is LlmResult.Token -> meSummaryText += result.text
                    is LlmResult.Complete -> {
                        isSummarizing = false
                        val now = System.currentTimeMillis()
                        summaryGeneratedAt = now
                        me.value?.let { db.personDao().updateSummary(it.id, meSummaryText, now) }
                    }
                    is LlmResult.Error -> isSummarizing = false
                    is LlmResult.ToolCall -> Unit
                    is LlmResult.PendingWrite -> Unit
                }
            }
        }
    }

    fun toggleTaskDone(task: Task) {
        viewModelScope.launch { db.taskDao().update(task.copy(isDone = !task.isDone)) }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch { db.taskDao().delete(task) }
    }
}
