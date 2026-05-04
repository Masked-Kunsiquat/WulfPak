# core-logic module

LLM orchestration, tool dispatch, family inference, and embedding/search pipeline. No UI here.

## @Tool constraint — read this first

`ContactsToolSet` uses LiteRT-LM's `@Tool` annotation. **Tool descriptions must be ≤ 180 characters** — longer strings silently break all tool dispatch for the session. Check length before committing any description change.

All `@Tool` methods run on LiteRT's thread pool. `runBlocking {}` is intentional and safe — it bridges the suspend DAO calls to LiteRT's sync interface. Do not remove it.

Parameter names are automatically snake_cased by LiteRT — write them in camelCase in Kotlin.

## ContactsToolSet — tool inventory

### Read-only tools

| Method | Description (≤180 chars) | Key behaviour |
|--------|--------------------------|---------------|
| `getContactNotes(name)` | Get notes for a contact by first name, or all recent notes across every contact if name is blank. | 15 most recent if blank |
| `getContactGifts(name)` | Get gift ideas and gifts for a contact by first name, or all pending gift ideas across every contact if name is blank. | Excludes GIVEN status |
| `getContactHistory(name)` | Get recent history with a specific person — use for 'how has X been', 'recent events with X'. Blank = last 30 days all contacts. | Interactions + activities |
| `getPendingTasks(name)` | Get all open (not yet done) tasks. Optionally filter by contact first name; leave blank for all pending tasks. | isDone = false only |
| `getContactCount()` | Get the total number of contacts. Use this to verify or answer questions about how many contacts the user has. | Excludes isMe person |
| `getContactDetails(name)` | Get a contact's profile — birthday, current age, relationship, job, and last contact date. | Includes age calc from birthday |
| `searchAcrossContacts(query)` | Keyword or topic search across notes, interactions, and activities — use for 'did I mention X', 'find Y conversation'. NOT for per-person history. | Semantic; ≤8 hits |
| `getUpcomingEvents()` | Get contacts with upcoming birthdays or anniversaries, sorted by soonest first. | Top 10 recurring life events |
| `getLapsedContacts(days)` | Get contacts you haven't been in touch with for a while, sorted by longest lapse first. | Default 60 days |
| `findContactsByRelation(relation)` | Find contacts by their relationship type — e.g. 'friend', 'colleague', 'family'. Fuzzy match. | Case-insensitive substring |
| `getLifeEvents(name)` | Get all life events recorded for a contact — birthday, anniversaries, job changes, moves, etc. | All types |
| `getRelationshipWeb(name)` | Get all person-to-person connections for a contact — family, colleagues, partners, etc. | Direct + inferred kin |
| `inferKinship(name)` | List all family relationships inferred for a contact via graph traversal. | BFS, excludes direct edges |
| `inferRelationBetween(nameA, nameB)` | Infer how two contacts are related to each other by traversing family edges. | Tries reverse if not found |
| `getClosenessInsight(name)` | Explain why a contact's closeness score has drifted from their rating. | Drift threshold: score < (rating-1)/4 − 0.15 |
| `getCurrentDateTime()` | Get the current date and time. Call this before logging anything dated, or when the user mentions 'today', 'yesterday', a specific date. | Returns formatted string |

### Write tools (staging pattern)

Write tools do **not** execute immediately. They stash a suspend block in `stagedWrites: Map<String, suspend () -> Unit>` and return a "Queued — pending user confirmation" description. The UI shows a confirm/cancel button. Execution via `executePendingWrite(id)` / `cancelPendingWrite(id)`.

| Method | Staged action |
|--------|---------------|
| `logInteraction(name, type, note, daysAgo)` | Insert Interaction + InteractionParticipant + call onInteractionAdded |
| `addNote(name, body, daysAgo)` | Insert Note with resolved timestamp |
| `addGiftIdea(name, giftName, occasion)` | Insert Gift with status = IDEA |
| `addTask(name, title, dueInDays)` | Insert Task with optional dueAt |

**`resolveTimestamp(daysAgo)`**: parses daysAgo string to Long or returns `System.currentTimeMillis()`.  
**`findPerson(name)`**: searches firstName, nickname, full name; falls back to phone number lookup via `PhoneUtils.normalizePhone` + `ContactDetail.value`.

## LlmOrchestrator — session model

**Constructor dependencies**: LlmProvider, all DAOs, SearchRepository, PersonRelationshipDao, FamilyInferenceEngine, SessionMemoryDao, ContactDetailDao, DebugEventLogger (optional).

No state persists between app restarts except `session_memories` in the DB.

### query(naturalLanguage) flow

1. Builds system prompt: `Prompts.QUERY_SYSTEM` + optional `YOUR PROFILE` section (if isMe person exists) + `RECENT SESSIONS` (5 most recent SessionMemory rows) + `CONTACTS` roster (up to 150, excluding isMe, names only)
2. Semantic search via `SearchRepository.search(query, 5)` → injects `RELEVANT RECORDS` into user message if hits found
3. Calls `provider.chatSend(userMsg, systemPrompt, listOf(contactsToolSet))`
4. Collects `LlmResult` stream; buffers ToolCall/PendingWrite events in `pendingBuffer` before first token
5. Logs `DebugEvent.LlmQuery(toolsUsed)` on completion

### Other entry points

| Method | Purpose | Prompt |
|--------|---------|--------|
| `summarize(personId)` | 2–3 sentence person summary | `Prompts.SUMMARIZE_SYSTEM` |
| `summarizeMe()` | Me dashboard AI summary | `Prompts.SUMMARIZE_SYSTEM` |
| `suggestFollowUp(personId, daysSinceContact)` | Reconnect message draft | `Prompts.FOLLOW_UP_SYSTEM` |
| `extractSessionMemory(conversationText)` | One-sentence session summary | `Prompts.SESSION_MEMORY_SYSTEM` |

**Session memory**: only user messages (not AI turns) are passed to `extractSessionMemory` — this prevents hallucinations from baking into future context.

**`resetChat()`**: clears `stagedWrites` and calls `provider.resetChat()`. Called before each new Search screen session.

## FamilyInferenceEngine

BFS graph traversal over `PersonRelationship` rows where `relType IS NOT NULL`.

- **`inferKinOf(personId)`** → `List<InferredKin>` — all reachable kin, excluding direct edges, up to depth 4
- **`inferBetween(idA, idB)`** → `String?` — label from A's perspective

Composed kin labels (selected examples): PARENT+PARENT → grandparent, PARENT+SIBLING → aunt/uncle, PARENT+SIBLING+CHILD → cousin, SPOUSE+PARENT → in-law, SPOUSE+SIBLING → brother/sister-in-law, CHILD+SPOUSE → daughter/son-in-law.

## Embedding pipeline

### EmbeddingProvider

- **Model**: Snowflake Arctic Embed XS TFLite (float32, 86 MB, 384-dim output)
- **Asset path**: `core-logic/src/main/assets/snowflake-arctic-embed-xs.tflite` + `vocab.txt`
- **Sequence length**: 128 tokens (padded/truncated)
- **Output**: masked mean pool of last hidden state → 384-dim FloatArray
- **`initialize(context)`** is async (called in `appScope` on app start). Check `isInitialized` before use — returns zero-vector if not ready.
- `generateEmbedding(text)` runs on `Dispatchers.Default`

### SearchRepository

`search(query, limit = 20)` → `List<SearchHit>` (sealed: NoteHit, InteractionHit, ActivityHit):
1. Embeds query
2. If zero-vector (model not initialized) → returns empty
3. Collects all embedded DB rows (notes, interactions, activities)
4. Cosine similarity against each; returns top N sorted by score

### EmbeddingWorker

One-shot `WorkManager` worker. Embeds all unembedded Notes (`note IS NOT NULL`), Interactions (with note), and Activities (`"title. body"`).

- `enqueue(wm)` — `ExistingWorkPolicy.KEEP` (don't interrupt in-progress)
- `enqueueNow(wm)` — `ExistingWorkPolicy.REPLACE` (force, used after demo seed import)
- Work name: `"embed_on_write"`
- Uses custom `WorkerFactory` wired via `AppApplication.workManagerConfiguration` — avoids opening a second DB connection

## Critical gotchas

- **Never add `alias(libs.plugins.kotlin.android)` to this module's build file.** AGP 9.x auto-applies KGP — double registration crashes at sync time.
- **180-char @Tool description limit**: exceeding it silently disables all tool calls for the session. Always count characters before committing.
- **`runBlocking` in @Tool methods is correct**: LiteRT calls tools synchronously; blocking bridges to suspend DAOs are intentional.
- **Write staging is not persisted**: `stagedWrites` is in-memory. If the process dies before confirmation, the staged write is lost (by design — no orphan writes).
- **Model not ready at cold start**: `EmbeddingProvider.initialize()` is async. Search and EmbeddingWorker check `isInitialized` and handle the not-ready case gracefully.
