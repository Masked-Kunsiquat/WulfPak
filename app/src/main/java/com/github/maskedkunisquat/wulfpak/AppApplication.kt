package com.github.maskedkunisquat.wulfpak

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.Configuration
import androidx.work.DelegatingWorkerFactory
import androidx.work.WorkManager
import com.github.maskedkunisquat.wulfpak.core.data.AppDatabase
import com.github.maskedkunisquat.wulfpak.core.data.db.KeyProvider
import com.github.maskedkunisquat.wulfpak.core.logic.embedding.EmbeddingProvider
import com.github.maskedkunisquat.wulfpak.core.logic.llm.LocalFallbackProvider
import com.github.maskedkunisquat.wulfpak.core.logic.llm.LlmOrchestrator
import com.github.maskedkunisquat.wulfpak.core.logic.search.SearchRepository
import com.github.maskedkunisquat.wulfpak.core.logic.worker.EmbeddingWorker
import com.github.maskedkunisquat.wulfpak.download.DownloadManagerModelDownloader
import com.github.maskedkunisquat.wulfpak.worker.ContactReminderWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppApplication : Application(), Configuration.Provider {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val db: AppDatabase by lazy {
        AppDatabase.create(this, KeyProvider.getOrCreateKey(this))
    }

    val embeddingProvider: EmbeddingProvider by lazy { EmbeddingProvider() }

    val llmProvider: LocalFallbackProvider by lazy {
        LocalFallbackProvider(
            context = this,
            modelDownloader = DownloadManagerModelDownloader(this),
        )
    }

    val searchRepository: SearchRepository by lazy {
        SearchRepository(
            embeddingProvider = embeddingProvider,
            noteDao = db.noteDao(),
            interactionDao = db.interactionDao(),
            activityDao = db.activityDao(),
        )
    }

    val llmOrchestrator: LlmOrchestrator by lazy {
        LlmOrchestrator(
            provider          = llmProvider,
            personDao         = db.personDao(),
            interactionDao    = db.interactionDao(),
            noteDao           = db.noteDao(),
            activityDao       = db.activityDao(),
            lifeEventDao      = db.lifeEventDao(),
            giftDao           = db.giftDao(),
            taskDao           = db.taskDao(),
            searchRepository  = searchRepository,
        )
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(DelegatingWorkerFactory().also { factory ->
                factory.addFactory(EmbeddingWorker.Factory(db, embeddingProvider))
                factory.addFactory(ContactReminderWorker.Factory(db, llmOrchestrator))
            })
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        appScope.launch {
            embeddingProvider.initialize(this@AppApplication)
        }
        appScope.launch(Dispatchers.IO) {
            if (llmProvider.isModelAvailable()) llmProvider.initialize()
        }
        ContactReminderWorker.schedule(WorkManager.getInstance(this))
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ContactReminderWorker.CHANNEL_ID,
                "Contact Reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Reminds you to reach out to people you haven't contacted recently"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
