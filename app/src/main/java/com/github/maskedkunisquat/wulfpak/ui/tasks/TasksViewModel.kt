package com.github.maskedkunisquat.wulfpak.ui.tasks

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.maskedkunisquat.wulfpak.AppApplication
import com.github.maskedkunisquat.wulfpak.core.data.entity.Person
import com.github.maskedkunisquat.wulfpak.core.data.entity.Task
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TasksViewModel(app: Application) : AndroidViewModel(app) {

    private val db = getApplication<AppApplication>().db

    data class TaskWithPerson(val task: Task, val person: Person?)

    val items = combine(
        db.taskDao().getAll(),
        db.personDao().getAll(),
    ) { tasks, persons ->
        val personMap = persons.associateBy { it.id }
        tasks.map { TaskWithPerson(it, it.personId?.let { pid -> personMap[pid] }) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleDone(task: Task) {
        viewModelScope.launch { db.taskDao().update(task.copy(isDone = !task.isDone)) }
    }

    fun delete(task: Task) {
        viewModelScope.launch { db.taskDao().delete(task) }
    }
}
