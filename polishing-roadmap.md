# WulfPak — Polishing Roadmap

Pre-feature coherence pass. Six concrete work areas, roughly ordered by dependency.

---

## 1 · Interactions — participant parity + edit access

**Files:** `AddEditInteractionScreen.kt`, `AddEditInteractionViewModel.kt`, `InteractionDetailScreen.kt`, `ContactsToolSet.kt`

- [x] `AddEditInteractionViewModel` — on edit load, call `interactionDao.getParticipantIds(id)` to populate `selectedIds`
- [x] `AddEditInteractionViewModel` — on save, loop-insert `InteractionParticipant` per selected ID (create); delete-all then re-insert (edit) — same pattern as `AddEditActivityViewModel`
- [x] `AddEditInteractionScreen` — add "Participants" section below existing fields (checkbox list, same pattern as `AddEditActivityScreen` lines 129–156); add search filter above list
- [x] `InteractionDetailScreen` — add Edit button in TopAppBar; navigate to `ADD_EDIT_INTERACTION/{firstParticipantId}?interactionId={id}`
- [x] `ContactsToolSet.getContactHistory` — append `"with: First Last, First Last"` to each activity/interaction line (skip if only the queried person)
- [x] `ContactsToolSet.searchAcrossContacts` — for each `ActivityHit` / `InteractionHit`, look up participants and append to the formatted line

---

## 2 · LLM tool descriptions — fix tool-selection failure

**File:** `ContactsToolSet.kt`

- [x] Rewrite `getContactHistory` `@Tool` description → e.g. `"Get recent history with a specific person — use for 'how has X been', 'recent events with X', 'what have I done with X'. Blank = last 30 days all contacts."` (keep ≤ 180 chars)
- [x] Rewrite `searchAcrossContacts` description to clarify it's keyword/topic search, not person-centric history
- [x] Spot-check all other `@Tool` descriptions for length (< 180 chars each) and clarity

---

## 3 · Tasks screen — computed tabs

**Files:** `TasksViewModel.kt`, `TasksScreen.kt`

Tab logic (no schema change):

| Tab | Condition |
|-----|-----------|
| Open | `!isDone && (dueAt == null \|\| dueAt > endOfTomorrow)` |
| Due Soon | `!isDone && dueAt != null && dueAt <= endOfTomorrow` |
| Overdue | `!isDone && dueAt != null && dueAt < now` |
| Done | `isDone` |

- [x] `TasksViewModel` — expose `selectedTab: Int`, `setTab(i)`, and 4 derived filtered lists
- [x] `TasksScreen` — replace flat `LazyColumn` with `TabRow` (4 tabs) + tab-switched `LazyColumn`
- [x] Add per-tab empty states ("No open tasks", "Nothing due soon", "Nothing overdue", "No completed tasks")

---

## 4 · Bottom nav rename: Search → Chat

**File:** `AppNavigation.kt`

- [x] Change `SEARCH` `BottomNavItem` label `"Search"` → `"Chat"`
- [x] Change icon `Icons.Default.Search` → `Icons.Default.Chat`

---

## 5 · Settings subscreens

**Files:** `AppNavigation.kt`, `SettingsScreen.kt`, new `*SettingsScreen.kt` files

- [x] Add 4 route constants: `SETTINGS_SECURITY`, `SETTINGS_DISPLAY`, `SETTINGS_AI`, `SETTINGS_CONTACTS`
- [x] Register 4 composable destinations in `AppNavigation`
- [x] `SettingsScreen` → convert to 4-row category list (NavigationListItems, no inline widgets)
- [x] `SecuritySettingsScreen` — biometric toggle (moved from SettingsScreen)
- [x] `DisplaySettingsScreen` — show age toggle (moved from SettingsScreen)
- [x] `AiSettingsScreen` — model download + Search index / Index now (Search index moved from Display)
- [x] `ContactsSettingsScreen` — merge contacts, import contacts, import vCard, export calendar

---

## 6 · Alphabetical sorting in person pickers

**Files:** `PersonDetailScreen.kt` (AddConnectionDialog), `AddEditActivityScreen.kt`, `AddEditActivityViewModel.kt`

- [x] `AddConnectionDialog` — `allPersons` sorted in `PersonDetailViewModel.loadAllPersons()` via DataStore preference; candidates inherit that order
- [x] `AddEditActivityViewModel` — `allPersons` is `combine(getAll(), sortByLastName)` so it re-sorts live when preference changes
- [x] `AddEditActivityScreen` — search `OutlinedTextField` above participant checklist; case-insensitive `contains` filter
- [x] `AddEditInteractionScreen` — same sort-via-combine applied to `AddEditInteractionViewModel`; search filter already existed from Phase 1
- [x] `AddEditTaskScreen` — no person picker; nothing to sort
- [x] **Bonus — Display settings toggle** — `SORT_BY_LAST_NAME` DataStore key; `SettingsViewModel.sortByLastName` + `setSortByLastName()`; "Sort contacts by last name" switch in `DisplaySettingsScreen`

---

## Deferred (design, not code)

**Dashboard** — revisit after ~1 month of real usage. Natural content: lapsed contacts, upcoming life events (30 days), recent feed summary, closeness snapshot.

**Closeness tracking / drift detection** — `closenessRating` (manual 1–5) = intended closeness; `closenessScore: Float?` (computed 0–1) = behavioral reality. Gap is the drift signal. Build in order below.

---

## D1 · Schema — `closenessScore` on Person (Room v5)

**Files:** `Person.kt`, `AppDatabase.kt`, `core-data/schemas/.../5.json`

- [x] Add `val closenessScore: Float? = null` to `Person` entity
- [x] Write migration v4→v5: `ALTER TABLE persons ADD COLUMN closenessScore REAL`
- [x] Export schema JSON (use `/room-migration` skill)
- [x] Add `@Query("UPDATE persons SET closenessScore = :score WHERE id = :id") suspend fun updateClosenessScore(id: UUID, score: Float?)` to `PersonDao`

---

## D2 · ClosenessCalculator

**File:** `core-logic/src/main/java/.../logic/closeness/ClosenessCalculator.kt`

Algorithm: decay-weighted interaction sum `Σ { typeWeight × durationBonus × 2^(−daysAgo/halfLife) }`, clamped 0–1. Grounded in Granovetter tie-strength theory + Facebook tie-strength study.

- [x] Create `ClosenessCalculator` — pure `object`, single `fun compute(interactions: List<Interaction>, category: String): Float`
- [x] Type weights: `IN_PERSON=1.0, VIDEO_CALL=0.8, CALL=0.6, TEXT=0.4, EMAIL=0.3, SOCIAL_MEDIA=0.2`
- [x] Duration bonus: `min(durationSeconds / 3600f, 1f)` (additive to weight, capped at 1 hr)
- [x] Half-life by `RelCategory`: `FAMILY=365d, FRIEND=150d, WORK=60d`; default `90d`
- [x] Normalize: divide raw sum by theoretical max for the period (~1 yr of weekly IN_PERSON 1-hr contacts), clamp to `0f..1f`
- [x] Add `suspend fun getForPersonOnce(personId: UUID): List<Interaction>` to `InteractionDao` (needed for eager recompute)

---

## D3 · Recompute on interaction save / delete

**Files:** `AddEditInteractionViewModel.kt`, `PersonDetailViewModel.kt` (or wherever interaction delete lives)

- [x] After `interactionDao.insert()` in `save()`, for each `sid` in `selectedIds`: fetch interactions via `interactionDao.getForPersonOnce(sid)`, compute score, call `personDao.updateClosenessScore(sid, score)`
- [x] Same recompute after `interactionDao.update()` (edit path) for all current participants
- [x] On interaction delete, recompute score for every affected participant before removing the row

---

## D4 · Drifting badge in People list

**Files:** `PeopleListScreen.kt`, `PeopleListViewModel.kt`

Drift condition: `closenessRating >= 4 && closenessScore != null && closenessScore < (closenessRating - 1) / 4f - 0.15f` (score is materially below the rating's expected band).

- [x] Expose `isDrifting: Boolean` as an extension on `Person` (or compute inline in the composable)
- [x] In `PeopleListScreen` `ListItem`, add a small `Text("⚠ Drifting", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)` in the `trailingContent` when `isDrifting` (alongside existing star/favourite icon)

---

## D5 · `getClosenessInsight` LLM tool

**Files:** `ContactsToolSet.kt`, `Prompts.kt`

- [x] Add `getClosenessInsight(name: String)` `@Tool` (use `/llm-tool` skill) — looks up person, reads `closenessScore` and `closenessRating`, narrates the drift in plain English; returns `"No closeness data yet"` if score is null
- [x] Description ≤ 180 chars: `"Explain why a contact's closeness score has drifted from their rating. Use for 'why am I drifting from X', 'how close am I to X', 'closeness insight for X'."` (156 chars)
- [x] Add one-line mention to system prompt in `Prompts.kt`

---

*Deferred:* Android system signal augmentation (call logs, SMS) — opt-in power feature, avoid sensitive permissions for now.

**ML Kit Entity Extraction** — defer. Notes are short and user-authored today. Revisit when notes grow longer or LLM summaries need to be mined back into structured fields.
