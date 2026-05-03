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

    private val _meId = MutableStateFlow<UUID?>(null)
    val meId: StateFlow<UUID?> = _meId

    init {
        viewModelScope.launch(Dispatchers.Default) {
            val persons = personDao.getAllOnce()
            val relationships = relationshipDao.getAllOnce()

            _meId.value = persons.find { it.isMe }?.id

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

            val me = persons.find { it.isMe }

            // Inter-contact edges from person_relationships rows
            val relEdges = relationships.mapNotNull { rel ->
                if (rel.personAId !in personMap || rel.personBId !in personMap) return@mapNotNull null
                val cat = runCatching { RelCategory.valueOf(rel.category) }.getOrDefault(RelCategory.OTHER)
                val score = personMap[rel.personAId]?.closenessScore ?: 0.3f
                GraphEdge(fromId = rel.personAId, toId = rel.personBId, category = cat, closenessScore = score)
            }

            // Synthesise Me → contact edges from Person.relationLabel (not in person_relationships)
            val meEdges = if (me != null) {
                persons.filter { !it.isMe }.map { contact ->
                    GraphEdge(
                        fromId = me.id,
                        toId = contact.id,
                        category = labelToCategory(contact.relationLabel),
                        closenessScore = contact.closenessScore ?: 0.3f,
                    )
                }
            } else emptyList()

            val edges = relEdges + meEdges

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

    private fun labelToCategory(label: String): RelCategory = when (label.lowercase()) {
        "mother", "father", "sibling", "child", "grandparent", "grandchild",
        "cousin", "aunt", "uncle", "step-parent", "step-child" -> RelCategory.FAMILY
        "friend", "best_friend", "acquaintance", "romantic_partner" -> RelCategory.FRIEND
        "colleague", "manager", "report", "mentor", "client" -> RelCategory.WORK
        else -> RelCategory.OTHER
    }
}
