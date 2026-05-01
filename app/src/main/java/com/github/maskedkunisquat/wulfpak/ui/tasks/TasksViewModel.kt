package com.github.maskedkunisquat.wulfpak.ui.tasks

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.maskedkunisquat.wulfpak.AppApplication
import com.github.maskedkunisquat.wulfpak.core.data.entity.Person
import com.github.maskedkunisquat.wulfpak.core.data.entity.Task
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class TasksViewModel(app: Application) : AndroidViewModel(app) {

    private val db = getApplication<AppApplication>().db

    data class TaskWithPerson(val task: Task, val person: Person?)

    private val allItems = combine(
        db.taskDao().getAll(),
        db.personDao().getAll(),
    ) { tasks, persons ->
        val personMap = persons.associateBy { it.id }
        tasks.map { TaskWithPerson(it, it.personId?.let { pid -> personMap[pid] }) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab

    fun setTab(i: Int) { _selectedTab.value = i }

    val openTasks = allItems.map { all ->
        all.filter { !it.task.isDone }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dueSoonTasks = allItems.map { all ->
        val sot = startOfToday()
        val eot = endOfTomorrow()
        all.filter { val due = it.task.dueAt; !it.task.isDone && due != null && due in sot..eot }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val overdueTasks = allItems.map { all ->
        val sot = startOfToday()
        all.filter { val due = it.task.dueAt; !it.task.isDone && due != null && due < sot }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val doneTasks = allItems.map { all ->
        all.filter { it.task.isDone }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleDone(task: Task) {
        viewModelScope.launch { db.taskDao().update(task.copy(isDone = !task.isDone)) }
    }

    fun delete(task: Task) {
        viewModelScope.launch { db.taskDao().delete(task) }
    }

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun endOfTomorrow(): Long = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis
}
