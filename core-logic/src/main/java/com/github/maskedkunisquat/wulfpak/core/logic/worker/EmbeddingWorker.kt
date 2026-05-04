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
import com.github.maskedkunisquat.wulfpak.core.logic.debug.DebugEvent
import com.github.maskedkunisquat.wulfpak.core.logic.debug.DebugLogger
import com.github.maskedkunisquat.wulfpak.core.logic.embedding.EmbeddingProvider
import java.nio.ByteBuffer
import java.nio.ByteOrder

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
    private val debugLogger: DebugLogger? = null,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val startMs = System.currentTimeMillis()
        if (!embeddingProvider.isInitialized) {
            Log.w(TAG, "EmbeddingProvider not initialized — retrying later")
            debugLogger?.log(DebugEvent.EmbeddingRun(
                notesEmbedded = 0, interactionsEmbedded = 0, activitiesEmbedded = 0,
                failed = false, durationMs = System.currentTimeMillis() - startMs, result = "skip",
            ))
            return Result.retry()
        }

        var notesEmbedded = 0
        var interactionsEmbedded = 0
        var activitiesEmbedded = 0
        var anyFailed = false

        db.noteDao().getUnembedded().forEach { note ->
            try {
                val vec = embeddingProvider.generateEmbedding(note.body)
                if (vec.any { it != 0f }) {
                    db.noteDao().updateEmbedding(note.id, vec.toBlob())
                    notesEmbedded++
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
                    db.interactionDao().updateEmbedding(interaction.id, vec.toBlob())
                    interactionsEmbedded++
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
                    db.activityDao().updateEmbedding(activity.id, vec.toBlob())
                    activitiesEmbedded++
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to embed activity ${activity.id}", e)
                anyFailed = true
            }
        }

        val total = notesEmbedded + interactionsEmbedded + activitiesEmbedded
        Log.i(TAG, "Embedded $total items")
        debugLogger?.log(DebugEvent.EmbeddingRun(
            notesEmbedded = notesEmbedded,
            interactionsEmbedded = interactionsEmbedded,
            activitiesEmbedded = activitiesEmbedded,
            failed = anyFailed,
            durationMs = System.currentTimeMillis() - startMs,
            result = if (anyFailed) "retry" else "success",
        ))
        return if (anyFailed) Result.retry() else Result.success()
    }

    class Factory(
        private val db: AppDatabase,
        private val embeddingProvider: EmbeddingProvider,
        private val debugLogger: DebugLogger? = null,
    ) : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker? = when (workerClassName) {
            EmbeddingWorker::class.java.name ->
                EmbeddingWorker(appContext, workerParameters, db, embeddingProvider, debugLogger)
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

        fun enqueueNow(workManager: WorkManager) {
            workManager.enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<EmbeddingWorker>().build()
            )
        }
    }
}

// Room's @Query treats FloatArray as a collection (IN clause), so we pre-serialize to a BLOB.
private fun FloatArray.toBlob(): ByteArray =
    ByteBuffer.allocate(size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        .apply { asFloatBuffer().put(this@toBlob) }.array()
