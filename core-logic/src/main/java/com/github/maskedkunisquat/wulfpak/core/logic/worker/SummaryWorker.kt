package com.github.maskedkunisquat.wulfpak.core.logic.worker

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.CoroutineWorker
import com.github.maskedkunisquat.wulfpak.core.data.AppDatabase
import com.github.maskedkunisquat.wulfpak.core.logic.llm.LlmOrchestrator
import com.github.maskedkunisquat.wulfpak.core.logic.llm.LlmResult
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Refreshes the cached summary for a single contact after a write event (new interaction, note, etc.).
 * Enqueue via [enqueue] — unique per person so rapid saves coalesce into one run.
 */
class SummaryWorker(
    context: Context,
    params: WorkerParameters,
    private val db: AppDatabase,
    private val llmOrchestrator: LlmOrchestrator,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val personIdStr = inputData.getString(KEY_PERSON_ID) ?: return Result.failure()
        val personId = try { UUID.fromString(personIdStr) } catch (_: IllegalArgumentException) { return Result.failure() }

        return try {
            val text = StringBuilder()
            var llmError: LlmResult.Error? = null
            llmOrchestrator.summarize(personId).collect { result ->
                when (result) {
                    is LlmResult.Token -> text.append(result.text)
                    is LlmResult.Error -> llmError = result
                    else               -> Unit
                }
            }
            if (llmError != null) {
                Log.w(TAG, "LLM error summarizing $personId: ${llmError!!.cause}")
                Result.retry()
            } else {
                val summary = text.toString().trim()
                if (summary.isNotEmpty()) {
                    db.personDao().updateSummary(personId, summary, System.currentTimeMillis())
                }
                Result.success()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Summary failed for $personId", e)
            Result.retry()
        }
    }

    class Factory(
        private val db: AppDatabase,
        private val llmOrchestrator: LlmOrchestrator,
    ) : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker? = when (workerClassName) {
            SummaryWorker::class.java.name ->
                SummaryWorker(appContext, workerParameters, db, llmOrchestrator)
            else -> null
        }
    }

    companion object {
        private const val TAG = "SummaryWorker"
        private const val KEY_PERSON_ID = "personId"

        fun enqueue(workManager: WorkManager, personId: UUID) {
            val data = Data.Builder().putString(KEY_PERSON_ID, personId.toString()).build()
            workManager.enqueueUniqueWork(
                "summary_$personId",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<SummaryWorker>()
                    .setInputData(data)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build(),
            )
        }
    }
}
