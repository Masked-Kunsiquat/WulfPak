package com.github.maskedkunisquat.wulfpak.core.logic.search

import com.github.maskedkunisquat.wulfpak.core.data.dao.ActivityDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.InteractionDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.NoteDao
import com.github.maskedkunisquat.wulfpak.core.data.entity.Activity
import com.github.maskedkunisquat.wulfpak.core.data.entity.Interaction
import com.github.maskedkunisquat.wulfpak.core.data.entity.Note
import com.github.maskedkunisquat.wulfpak.core.logic.embedding.EmbeddingProvider
import kotlinx.coroutines.flow.first

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

        val hits = mutableListOf<SearchHit>()

        noteDao.getAll().first().forEach { note ->
            val emb = note.embedding ?: return@forEach
            if (!emb.any { it != 0f }) return@forEach
            val score = CosineSimilarity.compute(queryVec, emb)
            if (score > 0f && score.isFinite()) hits += SearchHit.NoteHit(note, score)
        }

        interactionDao.getAll().first().forEach { interaction ->
            val emb = interaction.embedding ?: return@forEach
            if (!emb.any { it != 0f }) return@forEach
            val score = CosineSimilarity.compute(queryVec, emb)
            if (score > 0f && score.isFinite()) hits += SearchHit.InteractionHit(interaction, score)
        }

        activityDao.getAll().first().forEach { activity ->
            val emb = activity.embedding ?: return@forEach
            if (!emb.any { it != 0f }) return@forEach
            val score = CosineSimilarity.compute(queryVec, emb)
            if (score > 0f && score.isFinite()) hits += SearchHit.ActivityHit(activity, score)
        }

        return hits.sortedByDescending { it.score }.take(limit)
    }
}
