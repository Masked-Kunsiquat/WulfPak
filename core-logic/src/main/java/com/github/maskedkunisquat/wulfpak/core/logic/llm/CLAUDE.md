# llm package

`ContactsToolSet`, `LlmOrchestrator`, and `Prompts`. This is where all AI behaviour is defined.

## Adding a new @Tool

1. Add a `@Tool` annotated method to `ContactsToolSet`
2. **Description must be ≤ 180 characters** — exceeding this silently disables all tools for the session
3. Parameters use camelCase in Kotlin; LiteRT snake_cases them automatically for the model
4. All tool methods run on LiteRT's thread pool — use `runBlocking {}` to call suspend DAOs
5. Return a plain `String` — no markdown, no bullets (model is instructed to output plain text)
6. Add the tool name to `QUERY_SYSTEM`'s tool guidance section in `Prompts.kt`

## ContactsToolSet internals

**Sinks** (set by `LlmOrchestrator` before each session):
- `eventSink: ((LlmResult.ToolCall) -> Unit)?` — called from tool body; sends ToolCall event upstream
- `writeSink: ((LlmResult.PendingWrite) -> Unit)?` — called when a write is staged; sends PendingWrite event upstream

Both are `@Volatile`. Both are nullable — tools must null-check before calling.

**Staged writes**:
```
stagedWrites: ConcurrentHashMap<String, suspend () -> Unit>
```
Write tools generate a UUID id, put a suspend lambda in the map, call `writeSink` with the id + description, and return a "Queued" string. The lambda is never called until the user confirms.

- `executePendingWrite(id)` — removes from map and invokes the lambda
- `cancelPendingWrite(id)` — removes from map without invoking (no-op write)
- `clearStagedWrites()` — called by `resetChat()`; discards all unconfirmed writes

**`findPerson(name)`**: searches firstName, nickname, full name; falls back to `PhoneUtils.normalizePhone(name)` + `ContactDetail.value` lookup. Used by every tool that takes a name parameter.

**`resolveTimestamp(daysAgo)`**: parses `daysAgo` string as Int; returns `now - days * 86_400_000` or `System.currentTimeMillis()` if blank/invalid.

**`normalizeInteractionType(raw)`**: fuzzy maps free text to `InteractionType` enum constants (CALL TEXT EMAIL VIDEO_CALL SOCIAL_MEDIA IN_PERSON).

## Tool inventory

### Read tools (16)

| Method | Required params | Optional params | Notes |
|--------|-----------------|-----------------|-------|
| `getContactNotes` | — | `name` | 15 most recent if blank |
| `getContactGifts` | — | `name` | Excludes GIVEN status |
| `getContactHistory` | — | `name` | Last 30 days all contacts if blank |
| `getPendingTasks` | — | `name` | isDone=false only |
| `getContactCount` | — | — | Excludes isMe person |
| `getContactDetails` | `name` | — | Includes birthday age calc |
| `searchAcrossContacts` | `query` | — | Semantic; ≤8 hits |
| `getUpcomingEvents` | — | — | Top 10 recurring events |
| `getLapsedContacts` | — | `days` (default 60) | Sorted oldest first |
| `findContactsByRelation` | `relation` | — | Case-insensitive substring |
| `getLifeEvents` | `name` | — | All event types |
| `getRelationshipWeb` | `name` | — | Direct + inferred kin |
| `inferKinship` | `name` | — | BFS; excludes direct edges |
| `inferRelationBetween` | `nameA`, `nameB` | — | Tries reverse if not found |
| `getClosenessInsight` | `name` | — | Drift: score < (rating-1)/4 − 0.15 |
| `getCurrentDateTime` | — | — | Call before any dated operation |

### Write tools (4) — staging pattern

| Method | Required params | Optional params | Staged action |
|--------|-----------------|-----------------|---------------|
| `logInteraction` | `name`, `type` | `note`, `daysAgo` | Interaction + InteractionParticipant + onInteractionAdded |
| `addNote` | `name`, `body` | `daysAgo` | Note insert |
| `addGiftIdea` | `name`, `giftName` | `occasion` | Gift insert with status=IDEA |
| `addTask` | `name`, `title` | `dueInDays` | Task insert with optional dueAt |

## LlmOrchestrator — system prompt construction

`query()` builds the system prompt in this exact order:

```
[1] Prompts.QUERY_SYSTEM          — always
[2] YOUR PROFILE section          — only if getMe() returns non-null
[3] RECENT SESSIONS section       — only if session_memories table non-empty (5 most recent)
[4] CONTACTS roster               — always; up to 150 non-me contacts (names only)
                                    "(N more — use findContactsByRelation...)" if > 150
```

The user message is prefixed with `RELEVANT RECORDS` if `SearchRepository.search(query, 5)` returns hits.

**`pendingBuffer`**: tool events (ToolCall, PendingWrite) emitted while LiteRT is still running tools get buffered in `ArrayList<LlmResult>`. They are flushed to the collector just before the first Token arrives, so the UI displays tool activity inline above the response text.

## LlmResult sealed class

| Subclass | Fields | When emitted |
|----------|--------|--------------|
| `Token(text)` | `text: String` | Each streaming text chunk |
| `Complete` | — | End of response |
| `Error(cause)` | `cause: Throwable` | LLM or tool failure |
| `ToolCall(name, args)` | `name: String`, `args: Map<String, String>` | Each tool invocation |
| `PendingWrite(id, description)` | `id: String`, `description: String` | Each staged write queued |

## Prompts reference

### QUERY_SYSTEM
```
You are a personal CRM assistant. The user is asking about their contacts.
YOUR PROFILE (if present) describes the user themselves — use their name when addressing them.
The CONTACTS list is a name index — use it to resolve names and disambiguate. All details are in the tools.
Always call the appropriate tool rather than guessing or saying you don't have information.
When asked a factual question about contacts (count, details, history, tasks, etc.), call a tool to get the real answer — never rely on prior conversation or assumptions.
Tool guidance:
- Contact count or "how many contacts": call getContactCount
- Age, birthday, relationship, job: call getContactDetails
- Notes about someone: call getContactNotes
- Gifts or gift ideas: call getContactGifts
- Recent interactions or activities: call getContactHistory
- Tasks: call getPendingTasks
- Birthdays / anniversaries coming up: call getUpcomingEvents
- Search a topic, memory, or event: call searchAcrossContacts
- Connections between people: call getRelationshipWeb
- Family relationships: call inferKinship or inferRelationBetween
- Who to reconnect with: call getLapsedContacts
- All contacts of a certain type: call findContactsByRelation
- Life milestones: call getLifeEvents
- Closeness score: call getClosenessInsight
Write tools (logInteraction, addNote, addTask, addGiftIdea) queue an action for user confirmation. After calling a write tool, tell the user what you've queued and that they can confirm or cancel using the button that will appear. Do not say the action is done — it is pending.
Each user message may also begin with RELEVANT RECORDS from a semantic search.
Be brief and direct. Plain text only — no markdown, no bullet points.
```

### SUMMARIZE_SYSTEM
```
Write 2-3 plain sentences about the person described in the FACTS below.
Use ONLY the facts listed. Do not add, assume, or infer anything not explicitly stated.
Plain text only — no markdown, bullets, or headers.
```

### SESSION_MEMORY_SYSTEM
```
In one sentence, summarize what the user was focused on or asking about in this conversation.
Output only the sentence — no intro, no explanation.
```

### FOLLOW_UP_SYSTEM
```
Write a short, warm reconnect message the user can send to this person.
Output only the message text — no intro, no explanation, no quotation marks.
```

## Critical gotchas

- **180-char tool description limit**: silently breaks ALL tools for the session. Count before committing.
- **`runBlocking` is correct**: LiteRT calls tools synchronously; blocking to reach suspend DAOs is intentional and safe on LiteRT's thread pool.
- **Never call writeSink/eventSink without null-checking**: sinks are set per-session and may be null if a tool fires outside an active orchestrator session.
- **Write staging is in-memory only**: if the process dies before user confirmation the write is lost. This is intentional — no orphan DB writes.
- **Session memory uses user messages only**: `extractSessionMemory` receives only the user side of the conversation to avoid hallucinations baking into future context.
