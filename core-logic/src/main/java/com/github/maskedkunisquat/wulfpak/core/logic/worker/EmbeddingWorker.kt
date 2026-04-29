package com.github.maskedkunisquat.wulfpak.core.logic.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.github.maskedkunisquat.wulfpak.core.data.AppDatabase
import com.github.maskedkunisquat.wulfpak.core.logic.embedding.EmbeddingProvider

/**
 * One-shot worker that embeds all unembedded Notes, Interactions, and Activities.
 * Enqueue after every write that produces a text body (embed on write, not on read).
 *
 * Requires a custom [Factory] wired via WorkManager initializer in AppApplication so the
 * shared [AppDatabase] and [EmbeddingProvider] singletons are injected — no second DB open.
 */
class EmbeddingWorker(
    context: Context,
    params: WorkerParameters,
    private val db: AppDatabase,
    private val embeddingProvider: EmbeddingProvider,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!embeddingProvider.isInitialized) {
            Log.w(TAG, "EmbeddingProvider not initialized — retrying later")
            return Result.retry()
        }

        var embedded = 0
        var anyFailed = false

        db.noteDao().getUnembedded().forEach { note ->
            try {
                val vec = embeddingProvider.generateEmbedding(note.body)
                if (vec.any { it != 0f }) {
                    db.noteDao().updateEmbedding(note.id, vec)
                    embedded++
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to embed note ${note.id}", e)
                anyFailed = true
            }
        }

        db.interactionDao().getUnembedded().forEach { interaction ->
            val text = interaction.note ?: return@forEach
            try {
                val vec = embeddingProvider.generateEmbedding(text)
                if (vec.any { it != 0f }) {
                    db.interactionDao().updateEmbedding(interaction.id, vec)
                    embedded++
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to embed interaction ${interaction.id}", e)
                anyFailed = true
            }
        }

        db.activityDao().getUnembedded().forEach { activity ->
            val text = buildString {
                append(activity.title)
                activity.body?.let { append(". $it") }
            }
            try {
                val vec = embeddingProvider.generateEmbedding(text)
                if (vec.any { it != 0f }) {
                    db.activityDao().updateEmbedding(activity.id, vec)
                    embedded++
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to embed activity ${activity.id}", e)
                anyFailed = true
            }
        }

        Log.i(TAG, "Embedded $embedded items")
        return if (anyFailed) Result.retry() else Result.success()
    }

    class Factory(
        private val db: AppDatabase,
        private val embeddingProvider: EmbeddingProvider,
    ) : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker? = when (workerClassName) {
            EmbeddingWorker::class.java.name ->
                EmbeddingWorker(appContext, workerParameters, db, embeddingProvider)
            else -> null
        }
    }

    companion object {
        private const val TAG = "EmbeddingWorker"
        const val WORK_NAME = "embed_on_write"

        fun enqueue(workManager: WorkManager) {
            workManager.enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<EmbeddingWorker>().build()
            )
        }
    }
}
