# Hardening Roadmap

Issues surfaced by quality audit on 2026-05-01. Ordered by impact — do them top-to-bottom.

---

## P0 — Silent Data Loss ✓ DONE

### 1. ~~MergeRepository drops `category` / `relType` from rewritten edges~~ FIXED 2026-05-01
**File:** `app/…/merge/MergeRepository.kt` ~line 52

`PersonRelationship` rewrite now passes `rel.category` and `rel.relType` through; family edges (e.g. `PARENT_OF`) survive contact merges.

---

### 2. ~~`interactionCount` not updated on interaction edit~~ FIXED 2026-05-01
**File:** `app/…/ui/person/AddEditInteractionViewModel.kt` ~lines 86–107

Edit path now captures old participant IDs, computes the removed/added delta, and fires `onInteractionDeleted`/`onInteractionAdded` for each changed participant.

---

## P1 — Correctness & Stability

### 3. ~~`vm.load()` called on every recomposition~~ FIXED 2026-05-01
**Files:** `app/…/navigation/AppNavigation.kt` ~lines 177, 305

`vm.load` in `PERSON_DETAIL` composable wrapped in `LaunchedEffect(personIdStr)`. `ActivityDetailScreen` was already correct. Cached-summary init moved from `PersonDetailViewModel.init` into `load()` to eliminate the fragile init/load ordering dependency.

---

### 4. Dead read-lock in `LocalFallbackProvider` (deadlock on exception path)
**File:** `core-logic/…/llm/LocalFallbackProvider.kt` ~lines 148–154, 192–198

Both `processInternal` and `chatSendInternal` acquire the read lock twice: once via `withLock {}` and once via a raw `lock()`/`finally { unlock() }`. On the happy path reentrancy saves you. On the exception path (`.catch` → `switchToCpu()`) the unlock count is off by one, leaving the read lock permanently held — any subsequent write-lock (`resetChat`, `switchToCpu`) blocks forever.

Fix: remove the raw `lock()`/`unlock()` pair and use a single `withLock {}` block for the entire critical section.

---

## P2 — Privacy / Security

### 5. PII logged at `Log.i` in production
**File:** `core-logic/…/llm/ContactsToolSet.kt` ~lines 72, 97, 131, 204, 223, 261, 379, 398, 420, 433, 451, 486, 508, 527, 545

Contact names, nicknames, search queries, and task titles are logged at `Log.i` on every tool call. These logs are readable by ADB and by apps with `READ_LOGS` on rooted devices — directly contradicts the encrypted DB + biometric lock security posture.

```kotlin
// before
Log.i(TAG, "getContactNotes: ${person.firstName}")

// after
if (BuildConfig.DEBUG) Log.d(TAG, "getContactNotes: ${person.firstName}")
```

Apply to all PII-bearing log lines. Confirm R8 strips `Log.d` calls in release via `android.util.Log` optimization rules.

---

## P3 — Performance

### 6. N×M DAO round-trips per LLM tool call
**File:** `core-logic/…/llm/ContactsToolSet.kt`

`getContactHistory`, `getContactNotes`, and `getContactGifts` iterate every person and issue 2–4 DAO queries per person inside `runBlocking`. 50 contacts = 200-500 sequential queries per tool invocation.

Fix: add `getAllOnce()` methods (no filter) to `InteractionDao`, `ActivityDao`, `NoteDao`, and `GiftDao`, load the full table in one query, then filter in memory. This mirrors how `personDao.getAllOnce()` is already used elsewhere.

---

### 7. `SearchRepository` loads all embedding BLOBs on every semantic search
**File:** `core-logic/…/search/SearchRepository.kt` ~lines 31–51

`noteDao.getAll().first()`, `interactionDao.getAll().first()`, `activityDao.getAll().first()` pull the full entity (including a ~1.5 KB `FloatArray` embedding per row) regardless of whether the row has an embedding. At 500 interactions this is 750 KB of allocations per search.

Fix: add DAO queries that filter `WHERE embedding IS NOT NULL` and return a lightweight projection (id + embedding), not the full entity.

---

## P4 — Code Health

### 8. `calculateAge` duplicated in three places
**Files:**
- `app/…/ui/common/FormatUtils.kt`
- `core-logic/…/llm/FactExtractor.kt`
- `core-logic/…/llm/ContactsToolSet.kt`

Identical birthday-before-today logic copied three times. A leap-year or timezone fix must be applied in all three or they diverge silently.

Fix: move the canonical implementation to `core-data` as an extension function on `Long` (the birthday field type); the other two sites delegate to it.

---

### 9. `FactExtractor.extract()` is dead code
**File:** `core-logic/…/llm/FactExtractor.kt` ~lines 124–213

`extract()` is never called. Only `buildSummary()` is used (from `LlmOrchestrator`). `extract()` duplicates ~90 lines of `buildSummary()` logic (age calc, last-contact formatting, gift/task enumeration) and will drift silently.

Fix: delete `extract()`. If a structured-extraction path is needed later, build it from `buildSummary()` rather than maintaining a parallel copy.

---

### 10. CLAUDE.md says DB is version 4 — it's version 5
**File:** `CLAUDE.md`

`AppDatabase.kt` has `version = 5` with `MIGRATION_4_5`. Update the CLAUDE.md key-files note to "Room DB is at version 5."

---

### 11. `FAMILY_LABEL_TO_REL_TYPE` built with silent key collision
**File:** `app/…/ui/person/PersonDetailScreen.kt` ~lines 641–643

`FamilyRelType.entries.flatMap { … }.toMap()` silently overwrites duplicate keys (`"Sibling"` appears twice). Harmless for current symmetric labels, but any future asymmetric entry with a shared label would silently lose one direction.

Fix: use `buildMap { }` with an explicit `require(!contains(key))` check, or `groupBy` + assert-single.

---

## Quick-reference table

| # | Priority | File | Type |
|---|----------|------|------|
| 1 | P0 | `MergeRepository.kt` | Data loss |
| 2 | P0 | `AddEditInteractionViewModel.kt` | Data integrity |
| 3 | P1 | `AppNavigation.kt` + `PersonDetailViewModel.kt` | Correctness |
| 4 | P1 | `LocalFallbackProvider.kt` | Deadlock |
| 5 | P2 | `ContactsToolSet.kt` | Privacy |
| 6 | P3 | `ContactsToolSet.kt` | Performance |
| 7 | P3 | `SearchRepository.kt` | Performance |
| 8 | P4 | `FormatUtils.kt` / `FactExtractor.kt` / `ContactsToolSet.kt` | Duplication |
| 9 | P4 | `FactExtractor.kt` | Dead code |
| 10 | P4 | `CLAUDE.md` | Docs |
| 11 | P4 | `PersonDetailScreen.kt` | Brittleness |
