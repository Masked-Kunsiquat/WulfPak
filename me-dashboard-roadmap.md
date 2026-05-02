# Me Dashboard Roadmap

Personal dashboard surfaced through the "me" contact. Scoped 2026-05-02.

---

## Step 1 — Data layer

- [x] **`PersonDao.kt`** — add `observeMe(): Flow<Person?>` after `getMe()` (same query, no suspend, no schema change)

```kotlin
@Query("SELECT * FROM persons WHERE isMe = 1 LIMIT 1")
fun observeMe(): Flow<Person?>
```

- [x] **`LlmOrchestrator.kt`** — add `fun summarizeMe(): Flow<LlmResult>` at bottom of class
  - Guard: `personDao.getMe() ?: emit error + return`
  - Build aggregate facts string: contact count, interactions last 30 days (`interactionDao.getAllOnce()`), pending task count (`taskDao.getPending().first()`), top-5 by `closenessScore`
  - `emitAll(provider.process(prompt, Prompts.SUMMARIZE_SYSTEM))` — stream only; ViewModel persists on `LlmResult.Complete` (same split as `summarize(personId)`)

---

## Step 2 — MeViewModel

**New file:** `app/src/main/java/.../ui/me/MeViewModel.kt`

- [x] Class skeleton — `AndroidViewModel`, DB via `getApplication<AppApplication>().db`, `llm` via `getApplication<AppApplication>().llmOrchestrator`
- [x] `me: StateFlow<Person?>` — from `personDao.observeMe()`
- [x] `meLifeEvents: StateFlow<List<LifeEvent>>` — `me.filterNotNull().flatMapLatest { lifeEventDao.getForPerson(it.id) }` (needs `@OptIn(ExperimentalCoroutinesApi::class)`)
- [x] `totalContacts: StateFlow<Int>` — `personDao.getAll().map { it.count { p -> !p.isMe } }`
- [x] `interactionsThisMonth: StateFlow<Int>` — `interactionDao.getAll().map { filter by Calendar month-start timestamp }`
- [x] `feed: StateFlow<List<FeedItem>>` — `combine(interactionDao.getAll(), activityDao.getAll())` sorted desc, capped at 50; reuse `FeedItem` from `ActivityFeedViewModel.kt` (already public)
- [x] `personsById: StateFlow<Map<UUID, Person>>` — `personDao.getAll().map { it.associateBy { p -> p.id } }` (for resolving names in feed rows)
- [x] `rankedContacts: StateFlow<List<Person>>` — `personDao.getAll().map { filter !isMe, sort by closenessScore desc nulls last }`
- [x] `lapsingContacts: StateFlow<List<Person>>` — `personDao.getAll().map { filter !isMe && (lastContactedAt == null || lastContactedAt < now - 60d) }`
- [x] `allOpenTasks: StateFlow<List<TaskWithPerson>>` — `combine(taskDao.getPending(), allPersons)`; re-declare `data class TaskWithPerson(val task: Task, val person: Person?)` locally (don't import from `TasksViewModel`)
- [x] `meSummaryText`, `isSummarizing`, `summaryGeneratedAt` — `mutableStateOf`, same pattern as `PersonDetailViewModel`
- [x] `init` block — pre-load `cachedSummary` / `summaryGeneratedAt` from `personDao.getMe()`
- [x] `summarizeMe()` — guard `isSummarizing`, collect `llm.summarizeMe()`, append tokens, persist on `LlmResult.Complete` via `personDao.updateSummary(me.value!!.id, ...)`
- [x] `toggleTaskDone(task)` + `deleteTask(task)` — `viewModelScope.launch { db.taskDao().update/delete(...) }`

---

## Step 3 — MeScreen

**New file:** `app/src/main/java/.../ui/me/MeScreen.kt`

- [x] Composable signature: `fun MeScreen(onAddTask: () -> Unit, onEditTask: (Task) -> Unit, viewModel: MeViewModel = viewModel())`
- [x] `Scaffold` with `TopAppBar("Me")` and FAB gated to `selectedTab == 3`
- [x] `EmptyMeCard` private composable — centered Card: "Tap ··· on a contact and choose 'This is me'"
- [x] Show `EmptyMeCard` when `me == null`; rest of screen only when `me != null`
- [x] `MeHeader` private composable — copy top-section Row from `PersonDetailScreen` lines 247–276, strip `relationLabel`; show avatar (56dp), name, age from birthday life event via `calculateAge()`, nickname, jobTitle/company
- [x] `ScrollableTabRow` — tabs: Overview | Activity | Relationships | Tasks
- [x] **OverviewTab** — `LazyColumn` with stats Card ("X contacts · Y interactions this month") + `MeAiSummaryCard` (replicate private AI summary card from `PersonDetailScreen` lines 364–419)
- [x] **ActivityTab** — `LazyColumn` of `FeedItem` rows; `InteractionItem` → type + note (Interaction has no personId); `ActivityItem` → title + timestamp only
- [x] **RelationshipsTab** — two sections: "Closest" ranked list with `LinearProgressIndicator(closenessScore)` trailing; "Lapsing" list with time-since-contact supporting text
- [x] **TasksTab** — mirror private `TasksTab` in `PersonDetailScreen` lines 893–931; Checkbox leading, delete IconButton trailing, person name + due date supporting

---

## Step 4 — Navigation

**File:** `app/src/main/java/.../navigation/AppNavigation.kt`

- [x] Add `const val ME = "me"` to `Routes` object (after `TASKS`)
- [x] Add `import androidx.compose.material.icons.filled.Person` (not yet in this file)
- [x] Replace `TopLevelDest(Routes.TASKS, Icons.Default.Assignment, "Tasks")` in `TOP_LEVEL_DESTS` with `TopLevelDest(Routes.ME, Icons.Default.Person, "Me")`
- [x] Add `composable(Routes.ME) { MeScreen(onAddTask = ..., onEditTask = ...) }` (Tasks composable left in place)
- [x] Add `import com.github.maskedkunisquat.wulfpak.ui.me.MeScreen`
- [x] Leave `const val TASKS` and `Routes.addEditTask()` in place — still used by add/edit task navigation

---

## Compile checkpoints

```bash
./gradlew :app:compileDebugKotlin   # run after each step
./gradlew assembleDebug && ./gradlew installDebug   # final
```

---

## Manual test checklist

- [ ] Bottom nav shows "Me" (Person icon); Tasks tab gone
- [ ] No "me" contact set → empty-state card shown
- [ ] "me" contact set → header shows name, age, nickname, job
- [ ] All 4 tabs render without crash
- [ ] Overview: counts are accurate; AI blurb generates on tap
- [ ] Activity: items in recency order; interaction rows show person name
- [ ] Relationships: ranked list + lapsing section both render
- [ ] Tasks: open tasks listed; checkbox toggles; delete works; FAB → AddEditTask

---

## Open questions

- **Overview AI blurb**: on-demand button (consistent with contact summary UX) or auto-generate periodically?
- **Feed tab**: once Activity tab exists in Me, the standalone Feed tab may be redundant — defer until built
