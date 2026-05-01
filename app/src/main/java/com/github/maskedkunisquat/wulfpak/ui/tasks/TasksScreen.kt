package com.github.maskedkunisquat.wulfpak.ui.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.maskedkunisquat.wulfpak.core.data.entity.Task
import com.github.maskedkunisquat.wulfpak.ui.common.toDisplayDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    onAddTask: () -> Unit,
    onEditTask: (Task) -> Unit = {},
    viewModel: TasksViewModel = viewModel(),
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val openTasks by viewModel.openTasks.collectAsStateWithLifecycle()
    val dueSoonTasks by viewModel.dueSoonTasks.collectAsStateWithLifecycle()
    val overdueTasks by viewModel.overdueTasks.collectAsStateWithLifecycle()
    val doneTasks by viewModel.doneTasks.collectAsStateWithLifecycle()

    val tabLabels = listOf("Open", "Due Soon", "Overdue", "Done")
    val tabItems = listOf(openTasks, dueSoonTasks, overdueTasks, doneTasks)
    val emptyMessages = listOf("No open tasks", "Nothing due soon", "Nothing overdue", "No completed tasks")
    val visibleItems = tabItems[selectedTab]

    Scaffold(
        topBar = { TopAppBar(title = { Text("Tasks") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddTask) {
                Icon(Icons.Default.Add, contentDescription = "Add task")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = selectedTab) {
                tabLabels.forEachIndexed { i, label ->
                    Tab(
                        selected = selectedTab == i,
                        onClick = { viewModel.setTab(i) },
                        text = { Text(label) },
                    )
                }
            }
            if (visibleItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        emptyMessages[selectedTab],
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(visibleItems, key = { it.task.id }) { (task, person) ->
                        ListItem(
                            modifier = Modifier.clickable { onEditTask(task) },
                            leadingContent = {
                                IconButton(onClick = { viewModel.toggleDone(task) }) {
                                    Icon(
                                        imageVector = if (task.isDone) Icons.Default.CheckBox
                                                      else Icons.Default.CheckBoxOutlineBlank,
                                        contentDescription = if (task.isDone) "Mark incomplete" else "Mark done",
                                        tint = if (task.isDone) MaterialTheme.colorScheme.primary
                                               else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            headlineContent = {
                                Text(
                                    text = task.title,
                                    textDecoration = if (task.isDone) TextDecoration.LineThrough else null,
                                )
                            },
                            supportingContent = {
                                val due    = task.dueAt?.let { "Due ${it.toDisplayDate()}" }
                                val forWho = person?.let { "For ${it.firstName}" }
                                val label  = listOfNotNull(due, forWho).joinToString(" · ")
                                if (label.isNotEmpty()) Text(label)
                            },
                            trailingContent = {
                                IconButton(onClick = { viewModel.delete(task) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
