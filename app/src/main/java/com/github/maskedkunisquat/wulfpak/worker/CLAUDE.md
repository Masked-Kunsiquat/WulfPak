# worker package

All four `WorkManager` workers. Each has an inner `Factory` class that receives singletons from `AppApplication` — do not open the DB or instantiate heavy objects inside `doWork()`.

## Worker factory pattern

Every worker has an inner class:
```kotlin
class Factory(private val db: AppDatabase, /* other deps */) : WorkerFactory() {
    override fun createWorker(appContext, workerClassName, workerParameters): ListenableWorker? =
        if (workerClassName == MyWorker::class.java.name)
            MyWorker(appContext, workerParameters, db, /* other deps */)
        else null
}
```

All factories are registered in `AppApplication.workManagerConfiguration` via `DelegatingWorkerFactory`. Adding a new worker requires registering its `Factory` there — the default WorkManager factory won't wire the custom constructor params.

## Workers

### CallLogImportWorker

**Purpose**: Polls `CallLog.Calls` for new calls, matches them to contacts, and writes `PendingCallStub` entries to DataStore.

**Schedule**: Unique periodic, every 3 hours (`ExistingPeriodicWorkPolicy.KEEP`). Also scheduled at app start from `AppApplication.onCreate()`.

**doWork() steps**:
1. Read DataStore — bail early if `CALL_LOG_IMPORT_ENABLED != true` or `READ_CALL_LOG` permission missing
2. Build `phoneMap: Map<String, UUID>` — normalized phone → personId from `ContactDetailDao`; drops ambiguous numbers (more than one person)
3. Query `CallLog.Calls.CONTENT_URI` for entries since `CALL_LOG_LAST_POLLED`; columns: NUMBER, TYPE, DATE, DURATION
4. For each call: normalize phone → look up personId → map type int to "INCOMING"/"OUTGOING"/"MISSED" → create `PendingCallStub`
5. Deduplicate new stubs against existing ones by key `"personId:timestamp"`
6. Write merged list to `PENDING_CALL_STUBS` DataStore key (JSON string)
7. Update `CALL_LOG_LAST_POLLED` to max of current value and newest stub timestamp
8. Log `DebugEvent.CallLogImport(stubsFound, stubsAdded, durationMs)`

**Dependencies**: `AppDatabase` only (reads ContactDetails, writes nothing to Room).

---

### EmbeddingWorker

**Purpose**: Embeds all unembedded Notes, Interactions, and Activities into 384-dim float vectors stored as BLOBs.

**Schedule**:
- `enqueue(wm)` — `ExistingWorkPolicy.KEEP` — called after any write that creates embeddable content
- `enqueueNow(wm)` — `ExistingWorkPolicy.REPLACE` — called after demo seed import to force immediate run
- Work name: `"embed_on_write"`

**doWork() steps**:
1. Check `embeddingProvider.isInitialized` — if false, return `Result.retry()`
2. Fetch `noteDao.getUnembedded()` → embed `note.body` for each; skip zero-vectors
3. Fetch `interactionDao.getUnembedded()` (note IS NOT NULL) → embed note text
4. Fetch `activityDao.getUnembedded()` → embed `"title. body"` concatenation
5. Count successes/failures; return `Result.retry()` if any failed, else `Result.success()`
6. Log `DebugEvent.EmbeddingRun(notesEmbedded, interactionsEmbedded, activitiesEmbedded, failed, durationMs, result)`

**Dependencies**: `AppDatabase`, `EmbeddingProvider`, `DebugEventLogger`.

---

### SummaryWorker

**Purpose**: Regenerates `cachedSummary` for a single person via the LLM.

**Schedule**: One-shot per person, unique work name `"summary_$personId"`, `ExistingWorkPolicy.REPLACE` (coalesces rapid saves), exponential backoff starting at 30s.

**Enqueue**: `SummaryWorker.enqueue(workManager, personId)` — called from ViewModels after saving person edits.

**doWork() steps**:
1. Extract `personId` from `inputData` via `KEY_PERSON_ID`
2. Call `llmOrchestrator.summarize(personId)` and collect tokens into StringBuilder
3. On `LlmResult.Error`:
   - `IllegalArgumentException` → `Result.failure()` (permanent — person not found)
   - Other → `Result.retry()`
4. On success: call `personDao.updateSummary(personId, summary, now)`
5. Return `Result.success()`

**Dependencies**: `AppDatabase`, `LlmOrchestrator`.

---

### ContactReminderWorker

**Purpose**: Surfaces lapsed contacts (> 30 days) as a notification with an AI-generated reconnect suggestion.

**Schedule**: Unique periodic, every 24 hours (`ExistingPeriodicWorkPolicy.KEEP`). Scheduled at app start.

**doWork() steps**:
1. Find all persons where `lastContactedAt` is null or older than `THRESHOLD_DAYS` (30)
2. If none: return `Result.success()` immediately
3. Pick the person with the oldest `lastContactedAt`
4. Call `llmOrchestrator.suggestFollowUp(personId, daysSince)` — collect tokens into suggestion string
5. Build notification with person's name + suggestion (or generic fallback if LLM fails); include count of other lapsed contacts in body
6. Post to `NotificationManager` on channel `CHANNEL_ID`
7. Return `Result.success()`

**Dependencies**: `AppDatabase`, `LlmOrchestrator`.

## Adding a new worker

1. Create worker class with `(appContext, workerParameters, db, ...)` constructor
2. Add inner `Factory` class following the pattern above
3. Register factory in `AppApplication.workManagerConfiguration` (`DelegatingWorkerFactory.addFactory(...)`)
4. Add scheduling call in `AppApplication.onCreate()` if periodic, or an `enqueue()` helper if on-demand
