package com.github.maskedkunisquat.wulfpak.core.logic.family

import com.github.maskedkunisquat.wulfpak.core.data.AppDatabase
import com.github.maskedkunisquat.wulfpak.core.data.entity.FamilyRelType
import java.util.UUID

class FamilyInferenceEngine(private val db: AppDatabase) {

    private enum class Step {
        PARENT, CHILD, SPOUSE, SIBLING, HALF_SIBLING, STEP_PARENT, STEP_CHILD, GRANDPARENT, GRANDCHILD
    }

    private data class Edge(val neighborId: UUID, val step: Step)

    suspend fun inferKinOf(personId: UUID): List<InferredKin> {
        val adjacency = buildAdjacency()
        val persons = db.personDao().getAllOnce().associateBy { it.id }
        return bfs(personId, adjacency).mapNotNull { (id, label) ->
            val p = persons[id] ?: return@mapNotNull null
            val name = buildString {
                append(p.firstName)
                if (p.lastName != null) append(" ${p.lastName}")
            }
            InferredKin(id, name, label)
        }
    }

    suspend fun inferBetween(idA: UUID, idB: UUID): String? {
        val adjacency = buildAdjacency()
        return bfs(idA, adjacency).firstOrNull { (id, _) -> id == idB }?.second
    }

    private suspend fun buildAdjacency(): Map<UUID, List<Edge>> {
        val map = mutableMapOf<UUID, MutableList<Edge>>()
        for (rel in db.personRelationshipDao().getAllFamilyRelationshipsOnce()) {
            val type = runCatching { FamilyRelType.valueOf(rel.relType!!) }.getOrNull() ?: continue
            map.getOrPut(rel.personAId) { mutableListOf() }.add(Edge(rel.personBId, stepAtoB(type)))
            map.getOrPut(rel.personBId) { mutableListOf() }.add(Edge(rel.personAId, stepBtoA(type)))
        }
        return map
    }

    private fun stepAtoB(type: FamilyRelType): Step = when (type) {
        FamilyRelType.PARENT_OF       -> Step.CHILD
        FamilyRelType.SPOUSE_OF       -> Step.SPOUSE
        FamilyRelType.SIBLING_OF      -> Step.SIBLING
        FamilyRelType.HALF_SIBLING_OF -> Step.HALF_SIBLING
        FamilyRelType.STEP_PARENT_OF  -> Step.STEP_CHILD
        FamilyRelType.GRANDPARENT_OF  -> Step.GRANDCHILD
    }

    private fun stepBtoA(type: FamilyRelType): Step = when (type) {
        FamilyRelType.PARENT_OF       -> Step.PARENT
        FamilyRelType.SPOUSE_OF       -> Step.SPOUSE
        FamilyRelType.SIBLING_OF      -> Step.SIBLING
        FamilyRelType.HALF_SIBLING_OF -> Step.HALF_SIBLING
        FamilyRelType.STEP_PARENT_OF  -> Step.STEP_PARENT
        FamilyRelType.GRANDPARENT_OF  -> Step.GRANDPARENT
    }

    // BFS from seed; returns (personId, kinLabel) for every reachable kin within DEPTH_LIMIT.
    // Global visited set ensures shortest-path label wins (and naturally excludes "parent's spouse"
    // if that spouse is already recorded as the seed's own parent).
    private fun bfs(seed: UUID, adjacency: Map<UUID, List<Edge>>): List<Pair<UUID, String>> {
        val results = mutableListOf<Pair<UUID, String>>()
        val visited = mutableSetOf(seed)
        val queue = ArrayDeque<Pair<UUID, List<Step>>>()
        queue.add(seed to emptyList())

        while (queue.isNotEmpty()) {
            val (current, path) = queue.removeFirst()
            if (path.size >= DEPTH_LIMIT) continue
            for (edge in adjacency[current] ?: continue) {
                if (edge.neighborId in visited) continue
                visited.add(edge.neighborId)
                val newPath = path + edge.step
                val label = composeKinship(newPath)
                if (label != null) results.add(edge.neighborId to label)
                queue.add(edge.neighborId to newPath)
            }
        }
        return results
    }

    private fun composeKinship(path: List<Step>): String? = when (path) {
        // Direct edges
        listOf(Step.PARENT)       -> "parent"
        listOf(Step.CHILD)        -> "child"
        listOf(Step.SPOUSE)       -> "spouse"
        listOf(Step.SIBLING)      -> "sibling"
        listOf(Step.HALF_SIBLING) -> "half-sibling"
        listOf(Step.STEP_PARENT)  -> "step-parent"
        listOf(Step.STEP_CHILD)   -> "step-child"
        listOf(Step.GRANDPARENT)  -> "grandparent"
        listOf(Step.GRANDCHILD)   -> "grandchild"

        // Grandparent / grandchild chains
        listOf(Step.PARENT, Step.PARENT) -> "grandparent"
        listOf(Step.CHILD,  Step.CHILD)  -> "grandchild"

        // Great-grandparent / great-grandchild
        listOf(Step.PARENT, Step.PARENT, Step.PARENT) -> "great-grandparent"
        listOf(Step.CHILD,  Step.CHILD,  Step.CHILD)  -> "great-grandchild"

        // Aunts, uncles, nieces, nephews
        listOf(Step.PARENT, Step.SIBLING)      -> "aunt/uncle"
        listOf(Step.PARENT, Step.HALF_SIBLING) -> "half-aunt/uncle"
        listOf(Step.SIBLING, Step.CHILD)       -> "niece/nephew"

        // Cousins
        listOf(Step.PARENT, Step.SIBLING,      Step.CHILD) -> "cousin"
        listOf(Step.PARENT, Step.HALF_SIBLING, Step.CHILD) -> "half-cousin"

        // In-laws
        listOf(Step.SPOUSE,  Step.PARENT)  -> "parent-in-law"
        listOf(Step.SPOUSE,  Step.SIBLING) -> "sibling-in-law"
        listOf(Step.SIBLING, Step.SPOUSE)  -> "sibling-in-law"
        listOf(Step.CHILD,   Step.SPOUSE)  -> "child-in-law"

        // Step-relations derived from traversal
        listOf(Step.PARENT, Step.STEP_PARENT) -> "step-grandparent"
        // parent's spouse who isn't already the seed's own parent (handled by global visited set)
        listOf(Step.PARENT, Step.SPOUSE)      -> "step-parent"

        else -> null
    }

    companion object {
        private const val DEPTH_LIMIT = 4
    }
}
