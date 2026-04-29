package com.github.maskedkunisquat.wulfpak

import android.app.Application
import androidx.work.Configuration
import com.github.maskedkunisquat.wulfpak.core.data.AppDatabase
import com.github.maskedkunisquat.wulfpak.core.data.db.KeyProvider
import com.github.maskedkunisquat.wulfpak.core.logic.embedding.EmbeddingProvider
import com.github.maskedkunisquat.wulfpak.core.logic.llm.LocalFallbackProvider
import com.github.maskedkunisquat.wulfpak.core.logic.llm.LlmOrchestrator
import com.github.maskedkunisquat.wulfpak.core.logic.search.SearchRepository
import com.github.maskedkunisquat.wulfpak.core.logic.worker.EmbeddingWorker
import com.github.maskedkunisquat.wulfpak.download.DownloadManagerModelDownloader
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
            provider = llmProvider,
            personDao = db.personDao(),
            interactionDao = db.interactionDao(),
            noteDao = db.noteDao(),
        )
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(EmbeddingWorker.Factory(db, embeddingProvider))
            .build()

    override fun onCreate() {
        super.onCreate()
        // Initialize the embedding model on a background thread — 87 MB asset load, non-blocking.
        // generateEmbedding() returns zero-vectors safely until initialization finishes.
        appScope.launch {
            embeddingProvider.initialize(this@AppApplication)
        }
    }
}
