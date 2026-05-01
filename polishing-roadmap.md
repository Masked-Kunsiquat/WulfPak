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

- [ ] `AddConnectionDialog` — sort `candidates` by `firstName` before rendering
- [ ] `AddEditActivityViewModel` — sort `allPersons` by `firstName`
- [ ] `AddEditActivityScreen` — add search `OutlinedTextField` above participant checklist; filter by name (case-insensitive contains)
- [ ] `AddEditInteractionScreen` — same sort + search filter (from item 1 above)
- [ ] Check `AddEditTaskScreen` for any person picker; sort if present

---

## Deferred (design, not code)

**Dashboard** — revisit after ~1 month of real usage. Natural content: lapsed contacts, upcoming life events (30 days), recent feed summary, closeness snapshot.

**Closeness tracking** — start with a WorkManager periodic-prompt approach when ready. Needs: `closenessUpdatedAt` column on Person (migration), optional `ClosenessSnapshot` table for history. Algorithmic assist (deviation from behavior) as a phase 2 nudge.

**ML Kit Entity Extraction** — defer. Notes are short and user-authored today. Revisit when notes grow longer or LLM summaries need to be mined back into structured fields.
