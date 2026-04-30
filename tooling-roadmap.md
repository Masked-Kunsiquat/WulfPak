# AI Tooling & Feature Roadmap

Personal relationship manager — offline-first, on-device LLM (Gemma 3n E4B), vector search (Snowflake Arctic XS).

---

## Current tool inventory

| Tool | What it does |
|---|---|
| `getContactDetails` | Age, birthday, relationship, job, last contact |
| `getContactNotes` | Notes for one person or 15 most recent across all |
| `getContactGifts` | Gift ideas for one person or all pending |
| `getContactHistory` | Interactions + activities for one person or last 30 days |
| `getPendingTasks` | Open tasks for one person or all |
| `getUpcomingEvents` | Recurring birthdays/anniversaries sorted soonest first |

---

## Phase 1 — Fix semantic search (blocker, ~5 lines)

**What's broken:** The entire embedding pipeline is architecturally complete but
functionally dead. `EmbeddingWorker` exists and is registered in `WorkerFactory`,
but `EmbeddingWorker.enqueue(workManager)` is never called after a write. Every
`Note`, `Interaction`, and `Activity` embedding column is NULL. `SearchRepository`
correctly skips zero-vectors, so every search returns nothing.

**Fix:** Call `EmbeddingWorker.enqueue(workManager)` from the ViewModels that save
notes, interactions, and activities. One call per save — the worker deduplicates
by only processing unembedded rows.

**Why this unlocks everything:** The LLM already injects "RELEVANT RECORDS" (top-5
semantic hits) into every chat turn. Once embeddings are live, the AI immediately
gets per-turn context it has never had. No prompt changes needed.

---

## Phase 2 — `searchAcrossContacts` tool

Once embeddings work, expose them as a first-class tool so the AI can search on
demand rather than only at the start of each turn.

```
Tool: searchAcrossContacts(query: String)
Returns: top semantic hits across Notes, Interactions, and Activities with
         contact name, date, and the matched snippet.
```

**Use cases the user can ask:**
- "Who mentioned the camping trip?"
- "Find everyone I talked to about job hunting"
- "What did Sarah say about her sister?"

**System prompt addition:**
```
- searchAcrossContacts: full-text semantic search across all notes, interactions,
  and activities — use this when the user asks about a topic or memory, not a person
```

The current per-turn injection covers the most recent context; this tool handles
deliberate recall of older content.

---

## Phase 3 — Person-person relationships

### Data layer
The `PersonRelationship` table already exists in the schema:
```
PersonRelationship
  personAId  UUID (FK → Person, CASCADE)
  personBId  UUID (FK → Person, CASCADE)
  label      String  — "introduced me to", "married to", "child of", etc.
  PK: (personAId, personBId)
```
What's missing: a DAO with useful queries, and any UI that reads or writes it.

### DAO queries to add
```kotlin
getRelationshipsForPerson(personId): Flow<List<PersonRelationshipWithPerson>>
  — returns all edges where personAId or personBId = personId, with the
    other person's name hydrated

areConnected(idA: UUID, idB: UUID): Boolean
  — quick check for the graph edge

getConnectionPath(...)  — deferred; needs graph traversal
```

### UI
- **Person detail screen** — new "Connections" section (or sub-tab) that lists
  who this person is connected to, with labels. Tap a connection to navigate.
- **Add connection flow** — picker: select another contact + enter/choose label.
  Predefined labels: "introduced me to", "married to", "sibling", "parent of",
  "child of", "works with", "friend of". Custom string supported.
- No graph view yet (deferred per original plan) — list-first is fine.

### AI tool
```
Tool: getRelationshipWeb(name: String)
Returns: all person-to-person connections for the named contact, formatted as
         "Sarah → Bob: introduced me to" etc.
```

**Use cases:**
- "How do I know Sarah?"
- "Who is Bob related to?"
- "Who introduced me to Jake?"

---

## Phase 4 — Smart read tools

Low implementation cost, high daily-use value.

### `getLapsedContacts(days: Int = 60)`
Queries `Person.lastContactedAt`, returns everyone you haven't contacted in N days,
sorted by longest lapse first. Optionally filter by `relationLabel`.

**Use cases:**
- "Who should I call this week?"
- "Who haven't I talked to in two months?"
- "Which friends am I losing touch with?"

### `findContactsByRelation(relation: String)`
Filters `Person` by `relationLabel` (fuzzy match to handle "friend" → "best_friend").

**Use cases:**
- "Show me all my colleagues"
- "Who are my family members?"
- "List everyone from work"

### `getLifeEvents(name: String)`
Returns all `LifeEvent` rows for a person, not just birthdays — graduations,
job changes, moves, deaths.

**Use cases:**
- "Has anything big happened in Jake's life recently?"
- "When did Maria move?"
- "What life events have I recorded for my mom?"

---

## Phase 5 — Write tools with confirmation UI

### Tools
```
logInteraction(name, type, note?)  — creates Interaction row
addNote(name, body)                — creates Note row
addTask(name, title, dueInDays?)   — creates Task row
```

### Architecture
Write tools must NOT execute immediately like read tools do. The flow:

1. LLM decides to call a write tool
2. Tool emits `LlmResult.PendingWrite(toolName, humanDescription, executeFn)`
   instead of running immediately
3. UI renders a confirmation card (see below) — execution is suspended
4. User approves → `executeFn()` runs → result fed back to LLM → LLM confirms
5. User denies → "user declined" fed back to LLM → LLM acknowledges

### Confirmation card UI

Sits below the AI's text response, separated by a `HorizontalDivider`:

```
AI: "Got it, I'll add that note for Sarah."
─────────────────────────────────────────
  ✏️  Add note · Sarah Mitchell
      "Mentioned she's moving to Austin"
      [✓ Confirm]  [✗ Cancel]
```

After confirm:
```
  ✓  Note added to Sarah Mitchell
```

After cancel:
```
  ✗  Cancelled
```

**Reuse the existing tool-call bubble component** — add a `pending: Boolean`
flag. Pending write bubbles stay expanded and non-collapsible until resolved.
Resolved bubbles collapse to a single status line (same as current read-tool
bubbles but with ✓/✗ prefix).

**Modify/correct in v1:** Don't build inline editing. If the user wants to change
something, they deny and re-ask ("actually make the note say X instead"). The
multi-turn chat history means the AI carries the correction forward naturally.
Inline field editing can be a v2 enhancement once the confirmation pattern
proves out.

---

## Phase 6 — Future / deferred

- **Relationship graph view** — Obsidian-style canvas. Data is being captured
  from day one via `PersonRelationship`. Defer until Phase 3 list UI is proven.
- **Gemini Nano Prompt API** (API 35+) — swap in as secondary provider when
  available. `LlmProvider` interface already abstracts this.
- **Keyword search fallback** — for devices where the TFLite model fails to load.
  Simple FTS4 Room query across `Note.body`, `Interaction.note`, `Activity.title`.
- **`logActivity(participants[], title, body?)`** — multi-person write tool;
  more complex confirmation UI (participant picker).

---

## Suggested order

```
1. Fix EmbeddingWorker.enqueue() — 5 lines, unlocks everything semantic
2. searchAcrossContacts tool     — immediately useful once embeddings work
3. Person-person relationships   — DAO + UI + getRelationshipWeb tool together
4. Smart read tools              — getLapsed, findByRelation, getLifeEvents
5. Write tools + confirm UI      — logInteraction, addNote, addTask
6. Graph view                    — when Phase 3 list UI is settled
```
