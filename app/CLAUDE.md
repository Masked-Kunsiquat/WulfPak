# app module

Compose UI, navigation, workers, and application wiring. All singletons live in `AppApplication`.

## AppApplication — singletons

All are `lazy` properties. Access via `getApplication<AppApplication>()` in ViewModels.

| Property | Type | Notes |
|----------|------|-------|
| `appScope` | `CoroutineScope` | `SupervisorJob + Dispatchers.Default` |
| `debugEventLogger` | `DebugEventLogger` | Toggle-gated; no-ops when disabled |
| `db` | `AppDatabase` | SQLCipher encrypted; demo profile uses `wulfpak_demo.db` |
| `familyInferenceEngine` | `FamilyInferenceEngine` | BFS over typed family edges |
| `embeddingProvider` | `EmbeddingProvider` | Async init in `appScope`; check `isInitialized` |
| `llmProvider` | `LocalFallbackProvider` | Wraps LiteRT-LM; only usable after model download |
| `searchRepository` | `SearchRepository` | Semantic search over embedded records |
| `llmOrchestrator` | `LlmOrchestrator` | Query, summarize, follow-up, session memory |

**Profile system**: `AppApplication.activeProfile` reads from `SharedPreferences("active_profile")`. Demo profile opens a separate DB. `switchProfile(context, target)` writes the pref and restarts the app via `FLAG_ACTIVITY_CLEAR_TASK`. Closeness score backfill is per-profile (separate DataStore flag).

**WorkManager**: custom `DelegatingWorkerFactory` with four registered factories — `EmbeddingWorker`, `SummaryWorker`, `ContactReminderWorker`, `CallLogImportWorker`. Workers receive singletons via factory injection; do not open the DB independently.

## DataStore keys (`AppPrefsKeys`)

All keys live in `AppPreferences.kt`. DataStore named `"app_prefs"` (Tink-encrypted).

| Key | Type | Purpose |
|-----|------|---------|
| `BIOMETRIC_ENABLED` | Boolean | Lock on app resume |
| `SHOW_BIRTHDAY_AGE` | Boolean | Display setting |
| `SORT_BY_LAST_NAME` | Boolean | People list sort |
| `CLOSENESS_ACTIVITY_BACKFILL_V1` | Boolean | One-time flag per profile |
| `DEBUG_CAPTURE_ENABLED` | Boolean | Toggle debug event logging |
| `CALL_LOG_IMPORT_ENABLED` | Boolean | Background call log polling |
| `CALL_LOG_LAST_POLLED` | Long | Timestamp of last poll (ms) |
| `PENDING_CALL_STUBS` | String | JSON-serialised `List<PendingCallStub>` |

## Navigation routes

All constants in `Routes` object in `AppNavigation.kt`.

### Bottom nav destinations (5)
`PEOPLE_LIST`, `ACTIVITY_FEED`, `SEARCH`, `ME`, `GRAPH`

### Full route table

| Constant | Pattern | Screen |
|----------|---------|--------|
| `PERSON_DETAIL` | `person_detail/{personId}` | PersonDetailScreen |
| `ADD_EDIT_PERSON` | `add_edit_person?personId=…` | AddEditPersonScreen |
| `ADD_EDIT_INTERACTION` | `add_edit_interaction/{personId}?interactionId=…` | AddEditInteractionScreen |
| `ADD_EDIT_ACTIVITY` | `add_edit_activity?activityId=…` | AddEditActivityScreen |
| `ADD_EDIT_NOTE` | `add_edit_note/{personId}?noteId=…` | AddEditNoteScreen |
| `ADD_EDIT_LIFE_EVENT` | `add_edit_life_event/{personId}?lifeEventId=…` | AddEditLifeEventScreen |
| `ADD_EDIT_GIFT` | `add_edit_gift/{personId}?giftId=…` | AddEditGiftScreen |
| `ADD_EDIT_TASK` | `add_edit_task?personId=…&taskId=…` | AddEditTaskScreen |
| `ACTIVITY_DETAIL` | `activity_detail/{activityId}` | ActivityDetailScreen |
| `INTERACTION_DETAIL` | `interaction_detail/{interactionId}` | InteractionDetailScreen |
| `TASKS` | `tasks` | TasksScreen |
| `GRAPH` | `graph` | SocialGraphScreen |
| `ME` | `me` | MeScreen |
| `SETTINGS` | `settings` | SettingsScreen |
| `SETTINGS_SECURITY` | `settings/security` | SecuritySettingsScreen |
| `SETTINGS_DISPLAY` | `settings/display` | DisplaySettingsScreen |
| `SETTINGS_AI` | `settings/ai` | AiSettingsScreen |
| `SETTINGS_CONTACTS` | `settings/contacts` | ContactsSettingsScreen |
| `CONTACT_PICK` | `contact_pick` | ContactPickScreen |
| `MERGE_CONTACTS` | `merge_contacts` | MergeContactsScreen |
| `DEBUG_SUMMARY` | `debug_summary` | DebugSummaryScreen |
| `PENDING_CALLS` | `pending_calls` | PendingCallsScreen |

Use `Routes.personDetail(id)`, `Routes.addEditPerson(id?)`, etc. for safe argument interpolation.

**Settings sub-screens** share a `SettingsViewModel` instance via `getBackStackEntry(Routes.SETTINGS)` — all settings routes must be on the same back stack entry.

## ViewModel overview

| ViewModel | Owns | Key actions |
|-----------|------|-------------|
| `PendingCallsViewModel` | `pendingStubs` (Flow from DataStore), `confirmedStubs` (Compose state) | `skip`, `confirm` (DB + prefs), `saveNote` (DB then dismiss), `dismissConfirmed` |
| `SearchViewModel` | `query`, `messages` (chat history), `modelLoadState` | `askAi()` → streams LlmResult; saves session memory on exit |
| `MeViewModel` | `me`, feed (interactions + activities top 50), `rankedContacts`, `lapsingContacts`, `allOpenTasks` | Read-only; derived from DB flows |
| `SettingsViewModel` | Sync/import/export state machines | `performSync`, `importVCard`, `syncCalendarBirthdays`, `exportBackup`, `importBackup` |

## Workers

| Worker | Schedule | What it does |
|--------|----------|--------------|
| `EmbeddingWorker` | On write (enqueue KEEP); force after demo seed (REPLACE) | Embeds unembedded Notes, Interactions, Activities |
| `SummaryWorker` | Periodic | Regenerates `cachedSummary` for stale persons |
| `ContactReminderWorker` | Daily | Surfaces lapsed contacts via notification |
| `CallLogImportWorker` | 15-min periodic when enabled | Polls `CallLog.Calls` for new calls; matches to contacts via `PhoneUtils.normalizePhone`; writes to `PENDING_CALL_STUBS` DataStore key |

## PendingCallStub lifecycle

1. `CallLogImportWorker` matches call log entries to contacts → serialises to `PENDING_CALL_STUBS` (JSON)
2. `PendingCallsViewModel.pendingStubs` Flow deserialises on each DataStore update
3. `PendingCallsScreen` shows badge on People tab when stubs exist; tap badge → `PENDING_CALLS` route
4. User confirms → `confirm()`: DB transaction (Interaction + InteractionParticipant + onInteractionAdded) then prefs write to remove stub → `confirmedStubs` updated
5. User sees note prompt → `saveNote()`: DB insert then `dismissConfirmed` (dismiss only after successful insert)
6. User skips → `skip()`: prefs write removes stub, no DB write

**Non-atomicity**: DB transaction and DataStore removal are separate steps. If the app crashes between them the stub reappears but confirming again is handled by the UI (the interaction will be a duplicate — acceptable for a personal app).

**Stub validation**: `toPendingCallStubs()` validates `personId` as a valid UUID via `UUID.fromString()` and drops malformed entries — prevents crashes in `confirm()` / `saveNote()`.

## Debug Capture

`DebugEventLogger` is toggle-gated via `DEBUG_CAPTURE_ENABLED` pref. When disabled it no-ops all logging calls. Events are stored as NDJSON on disk; `DebugSummaryScreen` (route `DEBUG_SUMMARY`) shows an in-app analytics summary.

The Debug Capture row in `SettingsScreen` uses a split switch row: left zone navigates to `DebugSummaryScreen` (enabled only when capture is on and event count > 0); the `VerticalDivider` separates the two tap targets; the `Switch` on the right toggles capture independently.

## BiometricGate

Wraps `BiometricPrompt` for the single-Activity Compose setup. Accepts `BIOMETRIC_STRONG` or `DEVICE_CREDENTIAL` (PIN/pattern/password). Re-locks on `Activity.ON_STOP` — the gate re-engages every time the app is backgrounded. Cancels silently on user cancel / negative button (allows re-prompt without error state).

## Navigation debug logging

`AppNavigation` installs a `DisposableEffect` on `NavController.addOnDestinationChangedListener` and logs `DebugEvent.Nav(to, from)` for every route transition.
