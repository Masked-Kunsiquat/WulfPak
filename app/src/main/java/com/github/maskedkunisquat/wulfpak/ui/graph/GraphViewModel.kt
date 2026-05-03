package com.github.maskedkunisquat.wulfpak.ui.graph

import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.maskedkunisquat.wulfpak.AppApplication
import com.github.maskedkunisquat.wulfpak.core.data.entity.RelCategory
import com.github.maskedkunisquat.wulfpak.core.logic.graph.GraphLayoutEngine
import com.github.maskedkunisquat.wulfpak.core.logic.graph.GraphNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class GraphViewModel(app: Application) : AndroidViewModel(app) {

    private val personDao    = getApplication<AppApplication>().db.personDao()
    private val lifeEventDao = getApplication<AppApplication>().db.lifeEventDao()

    private val _nodes = MutableStateFlow<List<GraphNode>>(emptyList())
    val nodes: StateFlow<List<GraphNode>> = _nodes

    private val _positions = MutableStateFlow<Map<UUID, Offset>>(emptyMap())
    val positions: StateFlow<Map<UUID, Offset>> = _positions

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _meId = MutableStateFlow<UUID?>(null)
    val meId: StateFlow<UUID?> = _meId

    init {
        viewModelScope.launch {
            personDao.getAll().collectLatest { allPersons ->
                val deceasedIds = withContext(Dispatchers.IO) {
                    lifeEventDao.getDeceasedPersonIds().toSet()
                }
                val persons = allPersons.filter { it.id !in deceasedIds }
                val meId = persons.find { it.isMe }?.id
                _meId.value = meId

                val nodes = persons.map { person ->
                    GraphNode(
                        id             = person.id,
                        name           = person.firstName,
                        category       = labelToCategory(person.relationLabel),
                        closenessScore = person.closenessScore,
                        initials       = buildString {
                            person.firstName.firstOrNull()?.let { append(it) }
                            person.lastName?.firstOrNull()?.let  { append(it) }
                        }.uppercase().ifEmpty { "?" },
                        photoUri       = person.photoUri,
                    )
                }

                val positions = withContext(Dispatchers.Default) {
                    GraphLayoutEngine.layout(nodes = nodes, meId = meId)
                }
                _nodes.value = nodes
                _positions.value = positions
                _isLoading.value = false
            }
        }
    }

    private fun labelToCategory(label: String): RelCategory = when (label.lowercase()) {
        "mother", "father", "sibling", "child", "grandparent", "grandchild",
        "cousin", "aunt", "uncle", "step-parent", "step-child" -> RelCategory.FAMILY
        "friend", "best_friend", "acquaintance", "romantic_partner" -> RelCategory.FRIEND
        "colleague", "manager", "report", "mentor", "client" -> RelCategory.WORK
        else -> RelCategory.OTHER
    }
}
