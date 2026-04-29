package com.github.maskedkunisquat.wulfpak.worker

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.github.maskedkunisquat.wulfpak.R
import com.github.maskedkunisquat.wulfpak.core.data.AppDatabase
import com.github.maskedkunisquat.wulfpak.core.logic.llm.LlmOrchestrator
import com.github.maskedkunisquat.wulfpak.core.logic.llm.LlmResult
import java.util.concurrent.TimeUnit

class ContactReminderWorker(
    context: Context,
    params: WorkerParameters,
    private val db: AppDatabase,
    private val llmOrchestrator: LlmOrchestrator,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()
        val thresholdMs = THRESHOLD_DAYS * 24L * 60 * 60 * 1000

        val overdue = db.personDao().getAllOnce().filter { person ->
            val last = person.lastContactedAt ?: return@filter false
            (now - last) >= thresholdMs
        }
        if (overdue.isEmpty()) return Result.success()

        val top = overdue.maxByOrNull { p -> now - (p.lastContactedAt ?: 0L) }
            ?: return Result.success()
        val daysSince = ((now - (top.lastContactedAt ?: now)) / (24L * 60 * 60 * 1000)).toInt()

        val suggestion = try {
            val tokens = StringBuilder()
            llmOrchestrator.suggestFollowUp(top.id, daysSince).collect { result ->
                if (result is LlmResult.Token) tokens.append(result.text)
            }
            tokens.toString().trim().ifBlank { null }
        } catch (e: Exception) {
            Log.w(TAG, "LLM suggestion failed", e)
            null
        }

        val body = suggestion
            ?: "You haven't been in touch with ${top.firstName} in $daysSince days."
        val others = overdue.size - 1
        val title = if (others > 0) "Time to reconnect — ${others + 1} people" else "Time to reconnect"
        val fullBody = if (others > 0) {
            "$body\n\n+$others more: ${overdue.drop(1).take(5).joinToString(", ") { it.firstName }}"
        } else {
            body
        }

        postNotification(title, fullBody)
        return Result.success()
    }

    private fun postNotification(title: String, body: String) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID, notification)
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
            ContactReminderWorker::class.java.name ->
                ContactReminderWorker(appContext, workerParameters, db, llmOrchestrator)
            else -> null
        }
    }

    companion object {
        private const val TAG = "ContactReminderWorker"
        const val CHANNEL_ID = "contact_reminders"
        const val WORK_NAME = "contact_reminder_daily"
        private const val NOTIFICATION_ID = 1001
        private const val THRESHOLD_DAYS = 30

        fun schedule(workManager: WorkManager) {
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ContactReminderWorker>(24, TimeUnit.HOURS).build(),
            )
        }
    }
}
