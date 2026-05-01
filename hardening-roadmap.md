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

### 4. ~~Dead read-lock in `LocalFallbackProvider` (deadlock on exception path)~~ FIXED 2026-05-01
**File:** `core-logic/…/llm/LocalFallbackProvider.kt`

Eliminated the double-lock in `processInternal` and `chatSendInternal`: removed the first `withLock {}` (which acquired and immediately released the read lock just to read `engine`), and moved the null-check inside the single existing `lock()`/`finally { unlock() }` block. Now each function holds the read lock exactly once for its entire critical section.

---

## P2 — Privacy / Security

### 5. ~~PII logged at `Log.i` in production~~ FIXED 2026-05-01
**File:** `core-logic/…/llm/ContactsToolSet.kt`

All 18 `Log.i(TAG, ...)` calls converted to `if (BuildConfig.DEBUG) Log.d(TAG, ...)`. Enabled `buildConfig = true` in `core-logic/build.gradle.kts` to generate `BuildConfig.DEBUG`. R8 strips `Log.d` in release builds via its default `android.util.Log` optimization rules.

---

## P3 — Performance

### 6. ~~N×M DAO round-trips per LLM tool call~~ FIXED 2026-05-01
**Files:** `core-logic/…/llm/ContactsToolSet.kt`, `core-data/…/dao/{Interaction,Activity,Note,Gift}Dao.kt`

Added `getAllOnce()` to `InteractionDao`, `ActivityDao`, `NoteDao`, and `GiftDao`. The three all-contacts paths in `getContactNotes`, `getContactGifts`, and `getContactHistory` now issue 1–2 bulk queries and filter in memory (was N queries per person). `getContactHistory` also builds a `personById` map to replace O(N) `firstOrNull` lookups.

---

### 7. ~~`SearchRepository` loads all embedding BLOBs on every semantic search~~ FIXED 2026-05-01
**Files:** `core-logic/…/search/SearchRepository.kt`, `core-data/…/entity/EmbeddingRow.kt`, `core-data/…/dao/{Note,Interaction,Activity}Dao.kt`

Added `EmbeddingRow(id, embedding)` projection type. Each DAO now has `getEmbedded()` (`SELECT id, embedding … WHERE embedding IS NOT NULL`). `SearchRepository.search()` scores only the lightweight rows, sorts and takes top `limit`, then fetches full entities by ID only for the winners (at most 20 DB round-trips instead of loading every row).

---

## P4 — Code Health

### 8. ~~`calculateAge` duplicated in three places~~ FIXED 2026-05-01
**Files:**
- `app/…/ui/common/FormatUtils.kt`
- `core-logic/…/llm/FactExtractor.kt`
- `core-logic/…/llm/ContactsToolSet.kt`

Canonical `Long.calculateAge(asOfMs)` moved to `core-data/…/DateExtensions.kt`. All three sites now delegate to it; private `calculateAge`/`calcAge` copies deleted.

---

### 9. ~~`FactExtractor.extract()` is dead code~~ FIXED 2026-05-01
**File:** `core-logic/…/llm/FactExtractor.kt`

`extract()` and `longFmt` (only used by it) deleted. `buildSummary()` is the sole entry point; if a structured-extraction path is needed later it can be built from `buildSummary()` rather than a diverging parallel copy.

---

### 10. ~~CLAUDE.md says DB is version 4 — it's version 5~~ FIXED 2026-05-01
**File:** `CLAUDE.md`

Both the key-files entry and the architecture note updated to "Room v5". Fixed 2026-05-01.

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
| 8 | P4 ✓ | `DateExtensions.kt` (canonical) | Duplication |
| 9 | P4 ✓ | `FactExtractor.kt` | Dead code |
| 10 | P4 ✓ | `CLAUDE.md` | Docs |
| 11 | P4 | `PersonDetailScreen.kt` | Brittleness |
