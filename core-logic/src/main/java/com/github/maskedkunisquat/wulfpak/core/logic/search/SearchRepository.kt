package com.github.maskedkunisquat.wulfpak.core.logic.search

import com.github.maskedkunisquat.wulfpak.core.data.dao.ActivityDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.InteractionDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.NoteDao
import com.github.maskedkunisquat.wulfpak.core.data.entity.Activity
import com.github.maskedkunisquat.wulfpak.core.data.entity.EmbeddingRow
import com.github.maskedkunisquat.wulfpak.core.data.entity.Interaction
import com.github.maskedkunisquat.wulfpak.core.data.entity.Note
import com.github.maskedkunisquat.wulfpak.core.logic.embedding.EmbeddingProvider
import java.util.UUID

sealed class SearchHit {
    abstract val score: Float
    data class NoteHit(val note: Note, override val score: Float) : SearchHit()
    data class InteractionHit(val interaction: Interaction, override val score: Float) : SearchHit()
    data class ActivityHit(val activity: Activity, override val score: Float) : SearchHit()
}

class SearchRepository(
    private val embeddingProvider: EmbeddingProvider,
    private val noteDao: NoteDao,
    private val interactionDao: InteractionDao,
    private val activityDao: ActivityDao,
) {
    suspend fun search(query: String, limit: Int = 20): List<SearchHit> {
        val queryVec = embeddingProvider.generateEmbedding(query)
        if (!queryVec.any { it != 0f }) return emptyList()

        data class Candidate(val id: UUID, val score: Float, val kind: Int)
        val candidates = mutableListOf<Candidate>()

        fun collect(rows: List<EmbeddingRow>, kind: Int) {
            for (row in rows) {
                if (!row.embedding.any { it != 0f }) continue
                val s = CosineSimilarity.compute(queryVec, row.embedding)
                if (s > 0f && s.isFinite()) candidates += Candidate(row.id, s, kind)
            }
        }

        collect(noteDao.getEmbedded(), 0)
        collect(interactionDao.getEmbedded(), 1)
        collect(activityDao.getEmbedded(), 2)

        return candidates
            .sortedByDescending { it.score }
            .take(limit)
            .mapNotNull { (id, score, kind) ->
                when (kind) {
                    0 -> noteDao.getById(id)?.let { SearchHit.NoteHit(it, score) }
                    1 -> interactionDao.getById(id)?.let { SearchHit.InteractionHit(it, score) }
                    else -> activityDao.getById(id)?.let { SearchHit.ActivityHit(it, score) }
                }
            }
    }
}
