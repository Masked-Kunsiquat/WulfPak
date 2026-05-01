# AI Tooling & Feature Roadmap

Personal relationship manager — offline-first, on-device LLM (Gemma 4 E4B), vector search (Snowflake Arctic XS).

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
| `searchAcrossContacts` | Semantic search across notes, interactions, and activities by topic |

---

## ✅ Phase 1 — Fix semantic search

**Done.** Full embedding pipeline now working end-to-end:
- Float32 TFLite model (Snowflake Arctic XS, 86 MB) replaced broken float16 asset
- `EmbeddingWorker` enqueues after every write; "Index now" button forces re-index
- Fixed `@Query` FloatArray bug (Room was generating 384 `?` placeholders) — now pre-serializes to BLOB
- Verified: semantic search surfaces "House of Brews" and "second round of drinks" for "alcohol" query

---

## ✅ Phase 2 — `searchAcrossContacts` tool

**Done.** Implemented in `ContactsToolSet`. Uses `SearchRepository` cosine similarity
across Notes, Interactions, and Activities. Returns contact name, date, and matched snippet.
Wired into system prompt so the AI uses it when the user asks about a topic, not a person.

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

## ✅ Phase 4 — Smart read tools

**Done.** Four new tools added to `ContactsToolSet` and wired into `Prompts.QUERY_SYSTEM`:

- `getLapsedContacts(days = 60)` — filters `Person.lastContactedAt`, sorts by longest lapse; nulls (never contacted) sort first
- `findContactsByRelation(relation)` — fuzzy `contains()` match on `relationLabel` + underscore-replaced form
- `getLifeEvents(name)` — all `LifeEvent` rows for a person (uses `getForPersonOnce` added to `LifeEventDao`)
- `getRelationshipWeb(name)` — all person-to-person connections via `getConnectionsForPersonOnce`, with `effectiveLabel` for perspective-correct display

---

## ✅ Phase 5 — Write tools with confirmation UI

**Done.** Three write tools added. Flow: tool validates name, stages the DB write (keyed by UUID in `ContactsToolSet.stagedWrites`), emits `LlmResult.PendingWrite` via `writeSink`, returns a "queued" string to the model. Model's response tells the user to confirm. UI renders a `PendingWriteBubble` card; Confirm executes the staged write, Cancel discards it.

- `logInteraction(name, type, note?)` — creates Interaction + participant + updates `lastContactedAt`
- `addNote(name, body)` — creates Note linked to person
- `addTask(name, title, dueInDays?)` — creates Task with optional due date
- `addGiftIdea(name, giftName, occasion?)` — creates Gift with status IDEA

**Key files:** `LlmResult.kt` (PendingWrite), `ContactsToolSet.kt` (write tools + staged-write map), `LlmOrchestrator.kt` (writeSink wiring + executePendingWrite/cancelPendingWrite), `Prompts.kt` (write tool descriptions), `ChatMessage.kt` (PendingWrite + WriteState), `SearchViewModel.kt` (confirm/cancel methods), `SearchScreen.kt` (PendingWriteBubble)

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
- **Smart connection suggestions** — after saving an edge (e.g. Uncle ↔ Dad:
  Sibling), prompt: "Connect Uncle's spouse to Dad too?" Stays pair-wise under
  the hood; just reduces the tedium of dense groups.

---

## Family / large-group problem

The pair-wise connection model gets tedious fast for families. Three realistic
paths forward, in order of scope:

### A — Rich person notes (zero schema cost)
Add a free-text "family context" field to each Person (or just use Notes).
"Dad: my father, married to Mom, brother of Uncle Bob." The AI reads and
reasons over it naturally. Zero new schema, zero inference logic. Loses
queryability but covers the AI use-case well.

### B — Groups with roles (moderate, no GEDCOM creep)
New `Group` entity with a `GroupMembership(personId, groupId, role: String)`.
Example: Group "Paternal Family"; Dad=Father, Mom=Mother, Uncle=Uncle, Cousin=Cousin.
- Everyone's role is relative to **you**, not to each other — avoids the full graph.
- AI tool: `getGroupMembers(groupName)` returns roles.
- UI: group list screen + add-member flow on PersonDetail.
- Schema: 2 new tables, 1 migration, no inference engine.

### C — Transitive inference engine (full-send, GEDCOM-adjacent)
After recording Dad→Uncle: Sibling, infer Uncle's children are Cousins, Uncle's
spouse is Aunt-by-marriage, etc. Requires:
- Graph traversal (BFS/DFS) at query time or materialized at write time.
- A relationship-type ontology (what does "sibling of sibling's spouse" equal?).
- Edge cases: half-siblings, step-relationships, divorce, remarriage.
- This IS genealogy software. Only worth it if family mapping is a core value
  prop, not a side feature.

**Recommendation:** Option A if the AI use-case is the priority (quick win).
Option B if you want the app to feel family-aware without building a family tree.
Option C only if WulfPak pivots to being a family history tool.

---

## Suggested order

```
1. ✅ Fix EmbeddingWorker / float32 model  — semantic search live
2. ✅ searchAcrossContacts tool            — topic-based recall live
3. ✅ Person-person relationships          — DAO + UI + getRelationshipWeb tool
4. ✅ Smart read tools                     — getLapsed, findByRelation, getLifeEvents, getRelationshipWeb
5. ✅ Write tools + confirm UI             — logInteraction, addNote, addTask
6. Family strategy (A, B, or C above)     — decide before building graph view
7. Graph view                              — when Phase 3 list UI + family model settled
```
