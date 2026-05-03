package com.github.maskedkunisquat.wulfpak.ui.graph

import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.maskedkunisquat.wulfpak.AppApplication
import com.github.maskedkunisquat.wulfpak.core.data.entity.RelCategory
import com.github.maskedkunisquat.wulfpak.core.logic.graph.GraphEdge
import com.github.maskedkunisquat.wulfpak.core.logic.graph.GraphLayoutEngine
import com.github.maskedkunisquat.wulfpak.core.logic.graph.GraphNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class GraphViewModel(app: Application) : AndroidViewModel(app) {

    private val personDao = getApplication<AppApplication>().db.personDao()
    private val relationshipDao = getApplication<AppApplication>().db.personRelationshipDao()

    private val _nodes = MutableStateFlow<List<GraphNode>>(emptyList())
    val nodes: StateFlow<List<GraphNode>> = _nodes

    private val _edges = MutableStateFlow<List<GraphEdge>>(emptyList())
    val edges: StateFlow<List<GraphEdge>> = _edges

    private val _positions = MutableStateFlow<Map<UUID, Offset>>(emptyMap())
    val positions: StateFlow<Map<UUID, Offset>> = _positions

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        viewModelScope.launch(Dispatchers.Default) {
            val persons = personDao.getAllOnce().filter { !it.isMe }
            val relationships = relationshipDao.getAllOnce()

            // dominant category per person = most frequent RelCategory across their connections
            val categoryFreq = mutableMapOf<UUID, MutableMap<RelCategory, Int>>()
            for (rel in relationships) {
                val cat = runCatching { RelCategory.valueOf(rel.category) }.getOrDefault(RelCategory.OTHER)
                categoryFreq.getOrPut(rel.personAId) { mutableMapOf() }
                    .merge(cat, 1, Int::plus)
                categoryFreq.getOrPut(rel.personBId) { mutableMapOf() }
                    .merge(cat, 1, Int::plus)
            }

            val personMap = persons.associateBy { it.id }

            val nodes = persons.map { person ->
                val dominant = categoryFreq[person.id]?.maxByOrNull { it.value }?.key ?: RelCategory.OTHER
                GraphNode(
                    id = person.id,
                    name = person.firstName,
                    category = dominant,
                    closenessScore = person.closenessScore,
                )
            }

            val edges = relationships.mapNotNull { rel ->
                // skip edges where either person is not in our non-me set
                if (rel.personAId !in personMap || rel.personBId !in personMap) return@mapNotNull null
                val cat = runCatching { RelCategory.valueOf(rel.category) }.getOrDefault(RelCategory.OTHER)
                val score = personMap[rel.personAId]?.closenessScore ?: 0.3f
                GraphEdge(
                    fromId = rel.personAId,
                    toId = rel.personBId,
                    category = cat,
                    closenessScore = score,
                )
            }

            _nodes.value = nodes
            _edges.value = edges

            // Layout runs on Dispatchers.Default (already here); canvas size approximated at
            // 1080×1920 dp-pixels — ViewModel doesn't know screen size, GraphScreen re-triggers
            // layout via the same VM if needed; acceptable for v1.
            val positions = GraphLayoutEngine.layout(
                nodes = nodes,
                edges = edges,
                width = 1080f,
                height = 1920f,
            )
            _positions.value = positions
            _isLoading.value = false
        }
    }
}
