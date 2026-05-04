# core-data module

Room DB, entities, DAOs, and encryption. No business logic here.

## Database

- **Class**: `AppDatabase` — Room v8, SQLCipher encrypted
- **Key**: 32-byte random key generated once and stored in `EncryptedSharedPreferences` via `KeyProvider.getOrCreateKey()`
- **Schema export**: `exportSchema = true` → `core-data/schemas/` (checked in; required for migration history)
- **Demo profile**: `AppApplication` opens a separate `wulfpak_demo.db` file; same schema

## Migration history

| Version | Change |
|---------|--------|
| 1→2 | Add `company`, `jobTitle` (TEXT nullable) to `persons` |
| 2→3 | Add `cachedSummary`, `summaryGeneratedAt` (TEXT/INTEGER nullable) to `persons` |
| 3→4 | Add `category` (TEXT NOT NULL default `'OTHER'`) and `relType` (TEXT nullable) to `person_relationships`; backfill category from legacy label strings; normalize `Child` rows so parent UUID < child UUID |
| 4→5 | Add `closenessScore` (REAL nullable) to `persons` |
| 5→6 | Add `isMe` (INTEGER NOT NULL default 0) to `persons` |
| 6→7 | Create `session_memories` table: `id` TEXT PK, `timestamp` INTEGER, `summary` TEXT; index on timestamp |
| 7→8 | Safe no-op schema bump — regenerates `index_session_memories_timestamp`; no data changes |

## Entities

### Person (`persons`)
PK: `id` UUID  
`firstName`, `lastName`?, `nickname`?, `photoUri`?, `relationLabel`, `isFavorite`, `lastContactedAt`? (Long ms), `interactionCount` (default 0), `closenessRating`? (Int 1–5), `company`?, `jobTitle`?, `cachedSummary`?, `summaryGeneratedAt`? (Long), `closenessScore`? (Float 0–1), `isMe` (Boolean default false)

### ContactDetail (`contact_details`)
PK: `id` UUID · FK: `personId → persons` CASCADE · index: `personId`  
`type` (enum: PHONE EMAIL SOCIAL ADDRESS), `label`, `value`

### LifeEvent (`life_events`)
PK: `id` UUID · FK: `personId → persons` CASCADE · index: `personId`  
`eventType` (enum: birthday anniversary death graduation job_change moved), `date` Long, `isRecurring` Boolean, `note`?

### Interaction (`interactions`)
PK: `id` UUID  
`timestamp` Long, `type` (enum: CALL TEXT EMAIL VIDEO_CALL IN_PERSON SOCIAL_MEDIA), `durationSeconds`? Int, `note`?, `embedding`? (FloatArray 384-dim)  
**Note**: custom `equals`/`hashCode` handle FloatArray comparison

### InteractionParticipant (`interaction_participants`)
Composite PK: `interactionId` + `personId` · both FKs CASCADE · index: `personId`

### Activity (`activities`)
PK: `id` UUID  
`timestamp` Long, `title`, `body`?, `embedding`? (FloatArray 384-dim)  
Custom `equals`/`hashCode`

### ActivityParticipant (`activity_participants`)
Composite PK: `activityId` + `personId` · both FKs CASCADE · index: `personId`

### Note (`notes`)
PK: `id` UUID · FK: `personId → persons` nullable CASCADE · index: `personId`  
`timestamp` Long, `body`, `embedding`? (FloatArray 384-dim)  
Custom `equals`/`hashCode`

### Gift (`gifts`)
PK: `id` UUID · FK: `personId → persons` CASCADE · index: `personId`  
`name`, `occasion`?, `status` (enum: IDEA PURCHASED GIVEN; default IDEA), `note`?

### Task (`tasks`)
PK: `id` UUID · FK: `personId → persons` nullable CASCADE · index: `personId`  
`title`, `dueAt`? Long, `isDone` Boolean (default false)

### PersonRelationship (`person_relationships`)
Composite PK: `personAId` + `personBId` · both FKs CASCADE · index: `personBId`  
`label` String, `category` (enum: FAMILY FRIEND WORK OTHER; default OTHER), `relType`? (enum: PARENT_OF SPOUSE_OF SIBLING_OF HALF_SIBLING_OF STEP_PARENT_OF GRANDPARENT_OF)  
**Critical**: `personAId` UUID must be < `personBId` UUID (enforced in MIGRATION_3_4). Always insert via `addConnection()` which normalises ordering. Never write raw rows.

### SessionMemory (`session_memories`)
PK: `id` UUID · index: `timestamp`  
`timestamp` Long, `summary` String

## PersonConnection (DAO projection, not an entity)

`PersonRelationshipDao.getConnectionsForPerson()` returns `PersonConnection` which includes `effectiveLabel` — the label from the querying person's perspective. The raw `label` column is stored from personA's POV; `effectiveLabel` applies the `REVERSE_LABELS` map (Parent↔Child, Grandparent↔Grandchild, Step-parent↔Step-child, Aunt/Uncle↔Niece/Nephew; symmetric labels like Spouse/Sibling unchanged). Always use `effectiveLabel` for display.

## Key DAO methods

### PersonDao
- `getAll()` → `Flow<List<Person>>` — alphabetical by firstName, lastName
- `getAllOnce()` — suspend
- `getById(id)` / `observe(id)` — suspend / Flow
- `search(query)` — fuzzy on firstName, lastName, nickname
- `onInteractionAdded(personId, timestamp)` — updates `lastContactedAt`, increments `interactionCount`
- `onInteractionDeleted(personId)` — decrements `interactionCount`
- `getMe()` / `observeMe()` — `isMe = 1` row
- `clearAllMe()` / `setMe(id)` — maintain single isMe invariant
- `updateSummary(id, summary, generatedAt)` / `updateClosenessScore(id, score)`

### InteractionDao / ActivityDao (mirror structure)
- `getForPerson(personId)` — join on participants table, desc by timestamp
- `getUnembedded()` — `embedding IS NULL AND note IS NOT NULL` (for EmbeddingWorker)
- `getEmbedded()` → `List<EmbeddingRow>` (id + embedding only)
- `insertParticipant()` — `OnConflictStrategy.IGNORE` (idempotent)
- `updateEmbedding(id, embedding: ByteArray)` — stores as BLOB

### NoteDao
- `getStandalone()` — `personId IS NULL`
- `reassignToPerson(fromId, toId)` — used during contact merge

### TaskDao
- `getAll()` — ordered by `isDone ASC, dueAt ASC NULLS LAST`
- `getStandalone()` — `personId IS NULL`

### PersonRelationshipDao
- `getConnectionsForPerson(personId)` → `Flow<List<PersonConnection>>` — union query, use this for display
- `getForPerson(personId)` → raw `PersonRelationship` rows (both directions)
- `getAllFamilyRelationshipsOnce()` — `relType IS NOT NULL` (for inference engine)
- `insert()` — `OnConflictStrategy.REPLACE`

### SessionMemoryDao
- `insert(memory)`
- `getRecent(limit)` → `List<SessionMemory>` desc by timestamp

## TypeConverters

| Kotlin type | DB type | Converter |
|-------------|---------|-----------|
| UUID | TEXT | `UUID.toString()` / `UUID.fromString()` |
| FloatArray (384-dim) | BLOB | Little-endian ByteBuffer pack/unpack |
| List\<String\> | TEXT | JSON array via `JSONArray` |

## Critical gotchas

- **Never add `alias(libs.plugins.kotlin.android)` to this module's build file.** AGP 9.x auto-applies KGP to Android library modules — double registration crashes at sync time.
- **Schema export is required.** After any entity or DAO change that affects the schema, run `./gradlew :core-data:compileDebugKotlin` and commit the generated JSON in `core-data/schemas/`.
- **PersonRelationship UUID ordering**: personAId < personBId is a hard DB constraint (Migration 3_4). Use `addConnection()` — never insert `PersonRelationship` rows directly.
- **Embedding FloatArray equality**: Entities with `embedding` fields override `equals`/`hashCode` because arrays use reference equality by default. Don't remove those overrides.
