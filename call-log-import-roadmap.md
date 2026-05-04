# Call Log Import Roadmap

Auto-import calls from the Android call log into WulfPak interactions, with a confirmation queue before anything is written. Scoped 2026-05-03.

---

## Phase 1 — Phone number normalization utility

- [x] **`PhoneUtils.kt`** — new file in `:core-data`; single top-level `fun normalizePhone(raw: String): String` — strips all non-digit characters, drops a leading `1` if the result is 11 digits (US country code), returns the bare 10-digit string
- [x] Wire `normalizePhone` into `ContactsToolSet.findPerson()` for any future phone-number-based lookup path
- [x] This is the single source of truth — every call site in the app imports from here; no inline strip logic elsewhere

---

## Phase 2 — DataStore schema additions

- [ ] **`AppPrefsKeys`** — add `CALL_LOG_LAST_POLLED` (`longPreferencesKey("call_log_last_polled")`) — epoch ms of last successful sweep, reads as `0L` when absent
- [ ] **`AppPrefsKeys`** — add `PENDING_CALL_STUBS` (`stringPreferencesKey("pending_call_stubs")`) — JSON array string; empty string when absent
- [ ] **`PendingCallStub`** — new data class in `:app` (e.g. `app/.../model/PendingCallStub.kt`):

```kotlin
data class PendingCallStub(
    val personId: String,
    val personFirstName: String,
    val callType: String,        // "INCOMING" | "OUTGOING" | "MISSED"
    val durationSeconds: Int?,
    val timestamp: Long,
)
```

- [ ] Add `PendingCallStub.toJson()` and `List<PendingCallStub>.toJsonString()` / `String.toPendingCallStubs()` helpers using `org.json` (already on the Android classpath — no new dependency)

---

## Phase 3 — `CallLogImportWorker`

- [ ] **`CallLogImportWorker`** — new file in `app/.../worker/`; follows `ContactReminderWorker` pattern: `CoroutineWorker`, custom `Factory` injected with `AppDatabase` and `DataStore<Preferences>`
- [ ] In `doWork()`:
  - Read `CALL_LOG_LAST_POLLED` from DataStore (default `0L`)
  - Query `CallLog.Calls` for entries where `CallLog.Calls.DATE > lastPolled`, ordered by date ascending
  - For each entry: normalize the number with `PhoneUtils.normalizePhone`, query `contactDetails` where `type == "PHONE"` and normalized value matches; silently drop if no match
  - Build `PendingCallStub` for each matched entry; append to `PENDING_CALL_STUBS`
  - Update `CALL_LOG_LAST_POLLED` to `System.currentTimeMillis()`
- [ ] Register `CallLogImportWorker.Factory` in `AppApplication` alongside the existing worker factories
- [ ] Schedule via `WorkManager.enqueueUniquePeriodicWork("call_log_import", KEEP, every 3h)` in `AppApplication.onCreate()`

---

## Phase 4 — `READ_CALL_LOG` permission

- [ ] **`AndroidManifest.xml`** — add `<uses-permission android:name="android.permission.READ_CALL_LOG" />`
- [ ] **`AppPrefsKeys`** — add `CALL_LOG_IMPORT_ENABLED` (`booleanPreferencesKey("call_log_import_enabled")`)
- [ ] **Settings screen** — add "Auto-import calls" toggle row (below existing toggles); on first enable, trigger the `READ_CALL_LOG` runtime permission prompt via `rememberLauncherForActivityResult`; if denied, flip the toggle back and show a snackbar
- [ ] **`CallLogImportWorker.doWork()`** — guard at top: check `CALL_LOG_IMPORT_ENABLED` and `ContextCompat.checkSelfPermission(READ_CALL_LOG)`; return `Result.success()` silently if either is false/denied

---

## Phase 5 — Review UI

- [ ] **`PendingCallsViewModel`** — `AndroidViewModel`; exposes `pendingStubs: StateFlow<List<PendingCallStub>>` deserialized from `PENDING_CALL_STUBS`; `confirm(stub)` writes to `interactionDao` with `stub.timestamp` (exact call log timestamp — no `daysAgo` math needed) then removes stub from DataStore; `skip(stub)` just removes from DataStore
- [ ] **`PendingCallsScreen`** — `LazyColumn` of cards per stub: contact name, call type chip (Incoming / Outgoing / Missed), duration (formatted mm:ss or "—"), date; **Confirm** and **Skip** actions per card
- [ ] Badge or count chip on the home/contacts screen when `PENDING_CALL_STUBS` is non-empty; tapping navigates to `PendingCallsScreen`
- [ ] Add `Routes.PENDING_CALLS = "pending_calls"` and `composable(Routes.PENDING_CALLS)` to `AppNavigation`

---

## Phase 6 — Post-confirm LLM handoff

- [ ] After `confirm(stub)`, expand the card in-place to show an inline note text field with a "Save note" button — writes directly via `noteDao` using the same `stub.timestamp`
- [ ] "Ask assistant" chip on the expanded card navigates to the chat screen pre-seeded with: `"I just confirmed a [callType] with [personFirstName] from [date]. Want to add a note?"`

---

## Compile checkpoints

```bash
./gradlew :core-data:compileDebugKotlin   # after Phase 1
./gradlew :app:compileDebugKotlin         # after each subsequent phase
./gradlew assembleDebug && ./gradlew installDebug   # final
```

---

## Manual test checklist

- [ ] "Auto-import calls" toggle in Settings triggers permission prompt on first tap
- [ ] Denying permission flips the toggle back with a snackbar
- [ ] Worker runs and picks up calls made to known contacts (verify via WorkManager test run or `adb shell am broadcast`)
- [ ] Calls to unknown numbers are silently dropped — no stub created
- [ ] Badge appears on home/contacts screen when stubs are pending
- [ ] Confirm writes an interaction with the correct backdated timestamp
- [ ] Skip removes the stub without writing anything
- [ ] Inline note field saves a note with the same timestamp as the interaction
- [ ] "Ask assistant" opens chat with the pre-seeded context message
- [ ] Disabling the toggle stops future sweeps; existing stubs are unaffected
