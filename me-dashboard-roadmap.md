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

- [ ] Class skeleton — `AndroidViewModel`, DB via `getApplication<AppApplication>().db`, `llm` via `getApplication<AppApplication>().llmOrchestrator`
- [ ] `me: StateFlow<Person?>` — from `personDao.observeMe()`
- [ ] `meLifeEvents: StateFlow<List<LifeEvent>>` — `me.filterNotNull().flatMapLatest { lifeEventDao.getForPerson(it.id) }` (needs `@OptIn(ExperimentalCoroutinesApi::class)`)
- [ ] `totalContacts: StateFlow<Int>` — `personDao.getAll().map { it.count { p -> !p.isMe } }`
- [ ] `interactionsThisMonth: StateFlow<Int>` — `interactionDao.getAll().map { filter by Calendar month-start timestamp }`
- [ ] `feed: StateFlow<List<FeedItem>>` — `combine(interactionDao.getAll(), activityDao.getAll())` sorted desc, capped at 50; reuse `FeedItem` from `ActivityFeedViewModel.kt` (already public)
- [ ] `personsById: StateFlow<Map<UUID, Person>>` — `personDao.getAll().map { it.associateBy { p -> p.id } }` (for resolving names in feed rows)
- [ ] `rankedContacts: StateFlow<List<Person>>` — `personDao.getAll().map { filter !isMe, sort by closenessScore desc nulls last }`
- [ ] `lapsingContacts: StateFlow<List<Person>>` — `personDao.getAll().map { filter !isMe && (lastContactedAt == null || lastContactedAt < now - 60d) }`
- [ ] `allOpenTasks: StateFlow<List<TaskWithPerson>>` — `combine(taskDao.getPending(), allPersons)`; re-declare `data class TaskWithPerson(val task: Task, val person: Person?)` locally (don't import from `TasksViewModel`)
- [ ] `meSummaryText`, `isSummarizing`, `summaryGeneratedAt` — `mutableStateOf`, same pattern as `PersonDetailViewModel`
- [ ] `init` block — pre-load `cachedSummary` / `summaryGeneratedAt` from `personDao.getMe()`
- [ ] `summarizeMe()` — guard `isSummarizing`, collect `llm.summarizeMe()`, append tokens, persist on `LlmResult.Complete` via `personDao.updateSummary(me.value!!.id, ...)`
- [ ] `toggleTaskDone(task)` + `deleteTask(task)` — `viewModelScope.launch { db.taskDao().update/delete(...) }`

---

## Step 3 — MeScreen

**New file:** `app/src/main/java/.../ui/me/MeScreen.kt`

- [ ] Composable signature: `fun MeScreen(onAddTask: () -> Unit, onEditTask: (Task) -> Unit, viewModel: MeViewModel = viewModel())`
- [ ] `Scaffold` with `TopAppBar("Me")` and FAB gated to `selectedTab == 3`
- [ ] `EmptyMeCard` private composable — centered Card: "Tap ··· on a contact and choose 'This is me'"
- [ ] Show `EmptyMeCard` when `me == null`; rest of screen only when `me != null`
- [ ] `MeHeader` private composable — copy top-section Row from `PersonDetailScreen` lines 247–276, strip `relationLabel`; show avatar (56dp), name, age from birthday life event via `calculateAge()`, nickname, jobTitle/company
- [ ] `ScrollableTabRow` — tabs: Overview | Activity | Relationships | Tasks
- [ ] **OverviewTab** — `LazyColumn` with stats Card ("X contacts · Y interactions this month") + `MeAiSummaryCard` (replicate private AI summary card from `PersonDetailScreen` lines 364–419)
- [ ] **ActivityTab** — `LazyColumn` of `FeedItem` rows; `InteractionItem` → resolve person name from `personsById[interaction.personId]`; `ActivityItem` → title + timestamp only (no personId)
- [ ] **RelationshipsTab** — two sections: "Closest" ranked list with `LinearProgressIndicator(closenessScore)` trailing; "Lapsing" list with time-since-contact supporting text
- [ ] **TasksTab** — mirror private `TasksTab` in `PersonDetailScreen` lines 893–931; Checkbox leading, delete IconButton trailing, person name + due date supporting

---

## Step 4 — Navigation

**File:** `app/src/main/java/.../navigation/AppNavigation.kt`

- [ ] Add `const val ME = "me"` to `Routes` object (after `TASKS`)
- [ ] Add `import androidx.compose.material.icons.filled.Person` (not yet in this file)
- [ ] Replace `TopLevelDest(Routes.TASKS, Icons.Default.Assignment, "Tasks")` in `TOP_LEVEL_DESTS` (line 120) with `TopLevelDest(Routes.ME, Icons.Default.Person, "Me")`
- [ ] Replace `composable(Routes.TASKS)` NavHost block (lines 337–349) with `composable(Routes.ME) { MeScreen(onAddTask = ..., onEditTask = ...) }`
- [ ] Add `import com.github.maskedkunisquat.wulfpak.ui.me.MeScreen`
- [ ] Leave `const val TASKS` and `Routes.addEditTask()` in place — still used by add/edit task navigation

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
