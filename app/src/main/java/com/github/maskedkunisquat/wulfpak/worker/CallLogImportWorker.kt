package com.github.maskedkunisquat.wulfpak.worker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import android.provider.CallLog
import com.github.maskedkunisquat.wulfpak.AppPrefsKeys
import com.github.maskedkunisquat.wulfpak.appDataStore
import com.github.maskedkunisquat.wulfpak.core.data.AppDatabase
import com.github.maskedkunisquat.wulfpak.core.data.entity.ContactDetailType
import com.github.maskedkunisquat.wulfpak.core.data.normalizePhone
import com.github.maskedkunisquat.wulfpak.model.PendingCallStub
import com.github.maskedkunisquat.wulfpak.model.toPendingCallStubs
import com.github.maskedkunisquat.wulfpak.model.toJsonString
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class CallLogImportWorker(
    context: Context,
    params: WorkerParameters,
    private val db: AppDatabase,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val dataStore = applicationContext.appDataStore
        val prefs = dataStore.data.first()
        if (prefs[AppPrefsKeys.CALL_LOG_IMPORT_ENABLED] != true) return Result.success()
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.READ_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED) return Result.success()
        val lastPolled = prefs[AppPrefsKeys.CALL_LOG_LAST_POLLED] ?: 0L

        val phoneMap = db.contactDetailDao().getAllOnce()
            .filter { it.type == ContactDetailType.PHONE }
            .associate { normalizePhone(it.value) to it.personId }

        val people = db.personDao().getAllOnce()
            .filter { !it.isMe }
            .associateBy { it.id }

        val newStubs = mutableListOf<PendingCallStub>()

        val cursor = applicationContext.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION),
            "${CallLog.Calls.DATE} > ?",
            arrayOf(lastPolled.toString()),
            "${CallLog.Calls.DATE} ASC",
        )

        cursor?.use { c ->
            val colNumber   = c.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            val colType     = c.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            val colDate     = c.getColumnIndexOrThrow(CallLog.Calls.DATE)
            val colDuration = c.getColumnIndexOrThrow(CallLog.Calls.DURATION)
            while (c.moveToNext()) {
                val rawNumber = c.getString(colNumber) ?: continue
                val typeInt   = c.getInt(colType)
                val date      = c.getLong(colDate)
                val duration  = c.getInt(colDuration)

                val callType = when (typeInt) {
                    CallLog.Calls.INCOMING_TYPE -> "INCOMING"
                    CallLog.Calls.OUTGOING_TYPE -> "OUTGOING"
                    CallLog.Calls.MISSED_TYPE   -> "MISSED"
                    else -> continue
                }

                val personId = phoneMap[normalizePhone(rawNumber)] ?: continue
                val person   = people[personId] ?: continue

                newStubs += PendingCallStub(
                    personId        = personId.toString(),
                    personFirstName = person.firstName,
                    callType        = callType,
                    durationSeconds = if (callType == "MISSED" && duration == 0) null else duration,
                    timestamp       = date,
                )
            }
        }

        dataStore.edit { prefs ->
            if (newStubs.isNotEmpty()) {
                val existing    = (prefs[AppPrefsKeys.PENDING_CALL_STUBS] ?: "").toPendingCallStubs()
                val existingKeys = existing.map { "${it.personId}:${it.timestamp}" }.toSet()
                val deduped     = newStubs.filter { "${it.personId}:${it.timestamp}" !in existingKeys }
                if (deduped.isNotEmpty()) {
                    prefs[AppPrefsKeys.PENDING_CALL_STUBS] = (existing + deduped).toJsonString()
                }
            }
            prefs[AppPrefsKeys.CALL_LOG_LAST_POLLED] = System.currentTimeMillis()
        }

        return Result.success()
    }

    class Factory(private val db: AppDatabase) : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker? = when (workerClassName) {
            CallLogImportWorker::class.java.name ->
                CallLogImportWorker(appContext, workerParameters, db)
            else -> null
        }
    }

    companion object {
        const val WORK_NAME = "call_log_import"

        fun schedule(workManager: WorkManager) {
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<CallLogImportWorker>(3, TimeUnit.HOURS).build(),
            )
        }
    }
}
