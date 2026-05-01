# Family Relationship Roadmap

Personal relationship manager — offline-first, on-device LLM (Gemma 4 E4B).
This roadmap covers Option C: a transitive family inference engine, GEDCOM-adjacent.
The existing Friends/Work relationship system is preserved and extended, not replaced.

Reference spec: GEDCOM 5.5.1 with inline errata (`ged551-with-inline-errata.html`)

---

## Design philosophy

WulfPak stores relationships you actually know about. The inference engine derives
the relationships you haven't bothered to type in — once you tell it "Bob is my Dad's
sibling", it knows Bob is your uncle without you writing that down.

- **Typed family edges** are the ground truth: `PARENT_OF`, `SPOUSE_OF`, `SIBLING_OF`, etc.
- **Inferred labels** (cousin, aunt/uncle, in-law, etc.) are computed at query time by BFS.
- **Friends and Work** connections stay free-text with a curated label list — no inference.
- **Gender-neutral labels** throughout: "parent", "sibling", "aunt/uncle", "niece/nephew".
- No adoptive/guardianship edge types in Phase 1 (deferred — edge cases are significant).

---

## Relationship category system

Three buckets, chosen via a SegmentedPicker in the Add Connection dialog:

| Category | Label options |
|---|---|
| **Friends** | Friend, Best Friend, Family Friend, Introduced me, Custom… |
| **Family** | Parent, Child, Spouse, Sibling, Half-sibling, Step-parent, Step-child, Grandparent, Grandchild, Aunt/Uncle, Niece/Nephew, Cousin |
| **Work** | Colleague, Manager, Direct Report, Client, Mentor, Custom… |

"Family Friend" lives in **Friends** — it's a social connection, not a family edge, so
the inference engine never traverses it. Only **Family** connections carry a `relType`
and participate in kinship inference.

---

## Schema extension (DB version 3 → 4)

The `person_relationships` table gains two columns:

```
category  TEXT  NOT NULL  DEFAULT 'OTHER'   -- FAMILY | FRIEND | WORK | OTHER
relType   TEXT  NULL                        -- FamilyRelType name, null for non-FAMILY rows
```

### FamilyRelType enum (stored edge types)

Only directional/base types are stored as edges. The rest are inferred.

| Stored type | Stored as | Reverse (effectiveLabel) |
|---|---|---|
| `PARENT_OF` | personA is parent of personB | Child |
| `SPOUSE_OF` | symmetric | Spouse |
| `SIBLING_OF` | symmetric | Sibling |
| `HALF_SIBLING_OF` | symmetric | Half-sibling |
| `STEP_PARENT_OF` | personA is step-parent of personB | Step-child |
| `GRANDPARENT_OF` | personA is grandparent of personB | Grandchild |

### Migration 3→4 backfill

Existing rows are migrated best-effort based on label string:

| Existing label | → category | → relType |
|---|---|---|
| "Friend" | FRIEND | null |
| "Spouse" | FAMILY | SPOUSE_OF |
| "Sibling" | FAMILY | SIBLING_OF |
| "Parent" | FAMILY | PARENT_OF |
| "Child" | FAMILY | CHILD_OF* |
| "Colleague" | WORK | null |
| anything else | OTHER | null |

*CHILD_OF is normalized to PARENT_OF with swapped direction at insert time, consistent
with how all directional edges are stored (lower UUID = personA, direction tracked by isPersonA).

---

## Phase 1 — Schema & ontology

**Goal:** Lay the data foundation without breaking anything.

- [x] `FamilyRelType.kt` in `:core-data` — enum with `displayLabel` + `reverseLabel`
- [x] `RelCategory.kt` in `:core-data` — enum FAMILY / FRIEND / WORK / OTHER
- [x] Extend `PersonRelationship.kt` — add `category: String`, `relType: String?`
- [x] `PersonRelationshipDao.kt` — update `PersonConnection` data class + UNION ALL query to include new columns; add `getAllFamilyRelationshipsOnce()`
- [x] `AppDatabase.kt` — version 3→4, `MIGRATION_3_4` with backfill SQL
- [x] Update `REVERSE_LABELS` in `PersonRelationshipDao` to include FAMILY typed label strings

**Files:**
- `core-data/.../entity/PersonRelationship.kt`
- `core-data/.../entity/FamilyRelType.kt` *(new)*
- `core-data/.../entity/RelCategory.kt` *(new)*
- `core-data/.../dao/PersonRelationshipDao.kt`
- `core-data/.../AppDatabase.kt`

---

## Phase 2 — FamilyInferenceEngine

**Goal:** BFS graph traversal that derives kinship labels from typed edges.

Algorithm:
1. Load all `PersonRelationship` rows where `relType != null`
2. Build undirected adjacency map keyed by UUID, edges carry type + isPersonA direction
3. BFS from seed person, depth limit = 4 (covers great-grandparent)
4. At each hop, compose the traversal path into a kinship label using the rules below

### Kinship composition rules (GEDCOM-derived)

```
parent's parent                     → grandparent
parent's parent's parent            → great-grandparent
parent's sibling                    → aunt/uncle
parent's sibling's child            → cousin
parent's half-sibling               → half-aunt/uncle
parent's step-parent                → step-grandparent
sibling's child                     → niece/nephew
spouse's parent                     → parent-in-law
spouse's sibling                    → sibling-in-law
sibling's spouse                    → sibling-in-law
child's spouse                      → child-in-law
parent's spouse (non-parent)        → step-parent
```

Edge cases deferred: half-cousins, adoptive parents, divorce/remarriage graph cycles.
Cycles are detected by tracking visited UUIDs per BFS path.

### Deliberately omitted rule: "parent's child → sibling"

The path **you → Dad → half-brother** (two PARENT_OF hops) is *not* traversed to infer
"sibling". Reason: naively firing this rule always yields "sibling", but the correct label
(sibling vs. half-sibling) depends on whether the people share *both* parents — which
requires a second pass to compare the full parent sets of both nodes.

**Canonical approach:** tag a half-sibling directly with `HALF_SIBLING_OF`, and a full
sibling with `SIBLING_OF`. The shared-parent edges (Dad `PARENT_OF` you, Dad `PARENT_OF`
Bob) give the engine correct aunt/uncle/cousin inference for *Bob's* children and
parents without ever needing to derive the sibling edge from the graph.

Entering the shared parent relationship does not conflict with a direct `HALF_SIBLING_OF`
edge — the BFS skips already-visited nodes, so it will report the direct label and stop.

### Public API

```kotlin
class FamilyInferenceEngine(private val db: AppDatabase) {
    suspend fun inferKinOf(personId: UUID): List<InferredKin>
    suspend fun inferBetween(idA: UUID, idB: UUID): String?
}

data class InferredKin(val personId: UUID, val name: String, val kinLabel: String)
```

**Files:**
- `core-logic/.../FamilyInferenceEngine.kt` *(new)*
- `core-logic/.../InferredKin.kt` *(new)*

---

## Phase 3 — UI: SegmentedPicker + inferred kin section

**Goal:** Category-aware Add Connection dialog; inferred kin visible on PersonDetail.

### AddConnectionDialog changes

- `SingleChoiceSegmentedButtonRow`: **Friends | Family | Work** (above the label dropdown)
- Selecting a category repopulates the label dropdown with that bucket's options
- Family → typed FamilyRelType display labels; no custom field (types are fixed)
- Friends/Work → curated list + "Custom…" option with free-text field
- Save passes `(otherId, label, category, relType?)` — relType only set for Family

### ConnectionsTab changes

- Each connection row shows a small category chip (tonal, low-emphasis)
- New "Inferred family" subsection below explicit connections, populated by `FamilyInferenceEngine`
  - Shown only when the person has ≥1 family-typed edge
  - Italic text, secondaryContainer chip, non-navigable (inferred, not recorded)
- Empty state unchanged when no connections

### ViewModel changes

- `addConnection(otherId, label, category, relType?)` — updated signature
- `inferredKin: StateFlow<List<InferredKin>>` — loaded when Connections tab opens

**Files:**
- `app/.../person/PersonDetailScreen.kt`
- `app/.../person/PersonDetailViewModel.kt`

---

## Phase 4 — LLM tools

**Goal:** Expose family inference to the AI so it can answer kinship questions.

### New tools in ContactsToolSet

```
inferKinship(name)
  → "Jane is your cousin via Bob (your uncle). Sam is your parent-in-law."
  → Calls FamilyInferenceEngine.inferKinOf()
  → Description ≤ 180 chars

inferRelationBetween(nameA, nameB)
  → "Jane and Bob are siblings (both children of your parent Dave)."
  → Calls FamilyInferenceEngine.inferBetween()
  → Description ≤ 180 chars
```

### getRelationshipWeb update

When the person has family edges, append an "Inferred kin:" section to the output.

### System prompt update (Prompts.kt)

Add both new tools to `QUERY_SYSTEM` with usage guidance:
- `inferKinship`: "how is X related to me", "what relation is X", "is X my cousin"
- `inferRelationBetween`: "how are X and Y related", "what is X to Y"

### Wiring (AppApplication.kt)

`FamilyInferenceEngine(db)` instantiated as lazy singleton, passed into `LlmOrchestrator` → `ContactsToolSet`.

**Files:**
- `core-logic/.../llm/ContactsToolSet.kt`
- `core-logic/.../llm/Prompts.kt`
- `app/.../AppApplication.kt`

---

## Phase 5 — Graph view (deferred)

The data layer from Phases 1–2 provides everything the graph view needs:
typed edges, inferred edges, and the full adjacency structure. The graph canvas
can be built once the Phase 3 list UI is proven in daily use.

Planned: Obsidian-style canvas in `:app`, nodes = persons, edges = connections
(explicit + inferred). Layout algorithm TBD (force-directed preferred).

---

## Suggested order

```
1. Phase 1 — Schema + FamilyRelType enum + migration 3→4
2. Phase 2 — FamilyInferenceEngine + kinship rules
3. Phase 3 — SegmentedPicker UI + inferred kin section
4. Phase 4 — LLM tools
5. Phase 5 — Graph view (future)
```
