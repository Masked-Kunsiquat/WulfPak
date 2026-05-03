package com.github.maskedkunisquat.wulfpak

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Configuration
import androidx.work.DelegatingWorkerFactory
import androidx.work.WorkManager
import com.github.maskedkunisquat.wulfpak.core.data.AppDatabase
import com.github.maskedkunisquat.wulfpak.core.data.db.KeyProvider
import com.github.maskedkunisquat.wulfpak.core.logic.embedding.EmbeddingProvider
import com.github.maskedkunisquat.wulfpak.core.logic.family.FamilyInferenceEngine
import com.github.maskedkunisquat.wulfpak.core.logic.llm.LocalFallbackProvider
import com.github.maskedkunisquat.wulfpak.core.logic.llm.LlmOrchestrator
import com.github.maskedkunisquat.wulfpak.core.logic.search.SearchRepository
import com.github.maskedkunisquat.wulfpak.core.logic.worker.EmbeddingWorker
import com.github.maskedkunisquat.wulfpak.core.logic.worker.SummaryWorker
import com.github.maskedkunisquat.wulfpak.download.DownloadManagerModelDownloader
import com.github.maskedkunisquat.wulfpak.sync.BackupRepository
import com.github.maskedkunisquat.wulfpak.worker.ContactReminderWorker
import androidx.datastore.preferences.core.edit
import com.github.maskedkunisquat.wulfpak.core.logic.closeness.ClosenessCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AppApplication : Application(), Configuration.Provider {

    // ── Profile ───────────────────────────────────────────────────────────

    enum class Profile { REAL, DEMO }

    val activeProfile: Profile by lazy {
        val raw = profilePrefs().getString(PREF_ACTIVE_PROFILE, null)
        runCatching { Profile.valueOf(raw!!) }.getOrDefault(Profile.REAL)
    }

    val isDemoProfile: Boolean get() = activeProfile == Profile.DEMO

    // ── Core singletons ───────────────────────────────────────────────────

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val db: AppDatabase by lazy {
        val name = if (isDemoProfile) "wulfpak_demo.db" else "wulfpak.db"
        AppDatabase.create(this, KeyProvider.getOrCreateKey(this), name)
    }

    // Seeds the demo DB from the bundled asset on first demo launch.
    suspend fun seedDemoIfNeeded() {
        if (!isDemoProfile) return
        if (db.personDao().getAllOnce().isNotEmpty()) return
        resources.openRawResource(R.raw.seed_data).use { stream ->
            BackupRepository(db).importFromStream(stream)
        }
    }

    val familyInferenceEngine: FamilyInferenceEngine by lazy { FamilyInferenceEngine(db) }

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
            provider                = llmProvider,
            personDao               = db.personDao(),
            interactionDao          = db.interactionDao(),
            noteDao                 = db.noteDao(),
            activityDao             = db.activityDao(),
            lifeEventDao            = db.lifeEventDao(),
            giftDao                 = db.giftDao(),
            taskDao                 = db.taskDao(),
            searchRepository        = searchRepository,
            personRelationshipDao   = db.personRelationshipDao(),
            familyInferenceEngine   = familyInferenceEngine,
            sessionMemoryDao        = db.sessionMemoryDao(),
        )
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(DelegatingWorkerFactory().also { factory ->
                factory.addFactory(EmbeddingWorker.Factory(db, embeddingProvider))
                factory.addFactory(SummaryWorker.Factory(db, llmOrchestrator))
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
        val wm = WorkManager.getInstance(this)
        ContactReminderWorker.schedule(wm)
        EmbeddingWorker.enqueue(wm)    // backfill any rows written before model was ready
        appScope.launch(Dispatchers.IO) { backfillActivityClosenessScores() }
    }

    private suspend fun backfillActivityClosenessScores() {
        val prefs = appDataStore.data.first()
        if (prefs[AppPrefsKeys.CLOSENESS_ACTIVITY_BACKFILL_V1] == true) return
        db.personDao().getAllOnce().filter { !it.isMe }.forEach { person ->
            val interactions = db.interactionDao().getForPersonOnce(person.id)
            val activityTimestamps = db.activityDao().getTimestampsForPerson(person.id)
            val score = ClosenessCalculator.compute(
                interactions, activityTimestamps, ClosenessCalculator.categoryFor(person.relationLabel)
            )
            db.personDao().updateClosenessScore(person.id, score)
        }
        appDataStore.edit { it[AppPrefsKeys.CLOSENESS_ACTIVITY_BACKFILL_V1] = true }
    }

    private fun profilePrefs() =
        getSharedPreferences("profile_prefs", Context.MODE_PRIVATE)

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

    companion object {
        private const val PREF_ACTIVE_PROFILE = "active_profile"

        fun switchProfile(context: Context, target: Profile) {
            context.getSharedPreferences("profile_prefs", Context.MODE_PRIVATE)
                .edit().putString(PREF_ACTIVE_PROFILE, target.name).apply()
            val intent = context.packageManager
                .getLaunchIntentForPackage(context.packageName)!!
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Runtime.getRuntime().exit(0)
        }
    }
}
