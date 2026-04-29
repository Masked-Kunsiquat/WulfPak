# Monice Dopple — Agent Handoff Plan

Personal relationship manager (offline-first, encrypted, privacy-native Android app).
Conceptual target: Monica CRM, rebuilt for mobile with local AI.

---

## What's in this directory

Pre-cleaned, Lattice-derived source files. Rename the `com.yourapp` package to your final
app ID before wiring up. No CBT/journaling code is included — only the portable infrastructure.

| Directory | Files | Purpose |
|---|---|---|
| `embedding/` | `EmbeddingProvider.kt`, `WordPieceTokenizer.kt` | Snowflake Arctic XS TFLite pipeline, 384-dim |
| `db/` | `KeyProvider.kt`, `TypeConverters.kt` | SQLCipher key management, Room type converters |
| `biometric/` | `BiometricGate.kt` | Biometric + device-credential unlock wrapper |
| `llm/` | `LlmProvider.kt`, `LlmResult.kt`, `ModelLoadState.kt`, `LocalModelProvider.kt`, `ModelDownloader.kt`, `LocalFallbackProvider.kt` | Gemma 3 1B LiteRT-LM inference, full backend-selection + OpenCL fallback |
| `search/` | `CosineSimilarity.kt` | Cosine similarity math + usage documentation |

**Assets to copy from Lattice** (not duplicated here — too large for git):
- `core-logic/src/main/assets/snowflake-arctic-embed-xs_float32.tflite` (87 MB)
- `core-logic/src/main/assets/vocab.txt`
- Model `.litertlm` files are downloaded at runtime via `LocalFallbackProvider.downloadModel()`

---

## Privacy / security model

**No PiiShield needed.** The threat model here is physical device access, not data exfiltration.

| Layer | Mechanism |
|---|---|
| At-rest encryption | SQLCipher (AES-256), key in Android Keystore via `KeyProvider` |
| Access gate | `BiometricGate` — re-locks on every `ON_STOP` lifecycle event |
| LLM | 100% on-device (Gemma 3 1B LiteRT-LM). No cloud path in v1 |
| Embeddings | On-device TFLite. Raw contact text goes directly to the embedder |

No `TransitEvent` audit log needed. No masking step before embeddings.

---

## Schema (design from day one — even for deferred features)

Use Room + SQLCipher. Module split same as Lattice: `:core-data`, `:core-logic`, `:app`.

### Core entities

```
Person
  id: UUID (PK)
  firstName: String
  lastName: String?
  nickname: String?
  photoUri: String?               -- local file URI, nullable
  relationLabel: String           -- from RelationLabel constants; supports custom strings
  isFavorite: Boolean
  lastContactedAt: Long?          -- epoch ms, cached/denormalized from Interaction table
  interactionCount: Int           -- cached count, updated on every Interaction insert/delete
  closenessRating: Int?           -- user-set 0–5 for graph/ring view; nullable until set

ContactDetail                     -- phone, email, social handle, address
  id: UUID (PK)
  personId: UUID (FK → Person, CASCADE)
  type: ContactDetailType         -- PHONE, EMAIL, SOCIAL, ADDRESS
  label: String                   -- "mobile", "work", "Instagram", etc.
  value: String                   -- the actual number/address/handle

LifeEvent                         -- birthday, anniversary, death, custom
  id: UUID (PK)
  personId: UUID (FK → Person, CASCADE)
  eventType: String               -- from LifeEventType constants; supports custom strings
  date: Long                      -- epoch ms (store full timestamp; display as date only)
  isRecurring: Boolean            -- true for annual events like birthdays
  note: String?

Interaction                       -- call, text, email, video, in-person, etc.
  id: UUID (PK)
  timestamp: Long
  type: String                    -- from InteractionType constants; supports custom strings
  durationSeconds: Int?
  note: String?
  embedding: ByteArray            -- 384-dim FloatArray BLOB; zeroblob(1536) until embedded

InteractionParticipant            -- many-to-many: Interaction ↔ Person
  interactionId: UUID (FK → Interaction, CASCADE)
  personId: UUID (FK → Person, CASCADE)
  PK: (interactionId, personId)

Activity                          -- free-form "Add Activity" log; tagged to people
  id: UUID (PK)
  timestamp: Long
  title: String
  body: String?
  embedding: ByteArray            -- 384-dim BLOB

ActivityParticipant               -- many-to-many: Activity ↔ Person
  activityId: UUID (FK → Activity, CASCADE)
  personId: UUID (FK → Person, CASCADE)
  PK: (activityId, personId)

Note                              -- freeform note, optionally pinned to a person
  id: UUID (PK)
  personId: UUID?                 -- null = standalone note
  timestamp: Long
  body: String
  embedding: ByteArray            -- 384-dim BLOB

Gift                              -- gift tracker per person
  id: UUID (PK)
  personId: UUID (FK → Person, CASCADE)
  name: String
  occasion: String?               -- "Birthday 2025", etc.
  status: GiftStatus              -- IDEA, PURCHASED, GIVEN
  note: String?

Task                              -- per-person or standalone task
  id: UUID (PK)
  personId: UUID?                 -- null = standalone task
  title: String
  dueAt: Long?
  isDone: Boolean

PersonRelationship                -- person ↔ person edges (powers graph view later)
  personAId: UUID (FK → Person, CASCADE)
  personBId: UUID (FK → Person, CASCADE)
  label: String                   -- "introduced me to", "married to", "child of", etc.
  PK: (personAId, personBId)
```

### Enum / constant sets (use String columns, not enums, for extensibility)

```kotlin
object RelationLabel {
    // Family
    const val MOTHER = "mother"; const val FATHER = "father"
    const val SIBLING = "sibling"; const val CHILD = "child"
    const val GRANDPARENT = "grandparent"; const val COUSIN = "cousin"
    // Social
    const val FRIEND = "friend"; const val BEST_FRIEND = "best_friend"
    const val ACQUAINTANCE = "acquaintance"; const val ROMANTIC_PARTNER = "romantic_partner"
    // Professional
    const val COLLEAGUE = "colleague"; const val MANAGER = "manager"
    const val REPORT = "report"; const val MENTOR = "mentor"; const val CLIENT = "client"
}

object InteractionType {
    const val CALL = "call"; const val TEXT = "text"; const val EMAIL = "email"
    const val VIDEO_CALL = "video_call"; const val IN_PERSON = "in_person"
    const val SOCIAL_MEDIA = "social_media"
}

object LifeEventType {
    const val BIRTHDAY = "birthday"; const val ANNIVERSARY = "anniversary"
    const val DEATH = "death"; const val GRADUATION = "graduation"
    const val JOB_CHANGE = "job_change"; const val MOVED = "moved"
}
```

Using String columns (not enums) means custom values just work without a migration.

---

## What to build from scratch

### `:core-data` module
- All entities above + DAOs + Room database + migrations
- `AppTypeConverters` from `db/TypeConverters.kt` (already here — register on `@Database`)
- `KeyProvider` from `db/KeyProvider.kt` (already here)
- Contact import/export: Android `ContactsContract` ContentProvider for vCard/CSV import;
  `EZVCard` library for vCard parsing

### `:core-logic` module
- `EmbeddingProvider` + `WordPieceTokenizer` from `embedding/` (already here)
- `SearchRepository` — wire `CosineSimilarity.compute()` over Notes, Interactions, Activities
- `ContactSyncManager` — reads Android Contacts Provider, deduplicates, creates `Person` rows
- `CalendarBridge` — writes `LifeEvent` records as Android `CalendarContract` events for native
  reminder integration (read/write via ContentProvider — no CalDAV client needed)
- `LlmOrchestrator` — thin wrapper, single provider (no routing needed). Use cases:
    - Summarize a person's interaction history
    - Natural language query ("when did I last see Sarah?")
    - Suggest follow-up ("you haven't contacted X in 60 days")

### `:app` module
- `LocalFallbackProvider` from `llm/` (already here — update HF_BASE_URL + SHA256)
- `BiometricGate` from `biometric/` (already here)
- `LatticeApplication` → `AppApplication` singleton wiring (same `by lazy` pattern)
- Navigation graph: People list → Person detail → sub-screens (interactions, notes, events, gifts)
- Global: Activity feed, Search, Tasks
- Settings: Download model, biometric toggle, contact import

---

## Embedding strategy

Embed on write, not on read. Every `Note`, `Interaction.note`, and `Activity.body` gets
embedded immediately after save (background coroutine). Store as 384-dim FloatArray BLOB
(1536 bytes). Zero-vector = not yet embedded; filter before cosine search.

No NLP heads. No on-device training. Cosine similarity over raw Arctic embeddings is
sufficient for all search/retrieval use cases in a CRM.

---

## Relationship strength / graph data strategy

Capture from day one even though UI is deferred:

1. `interactionCount` and `lastContactedAt` on `Person` — updated on every Interaction
   insert/delete. Powers "closeness ring" view without a slow aggregate query.
2. `closenessRating: Int?` on `Person` — explicit 0–5 user rating. Nullable; unset until
   the user touches it. Works even with zero interaction history.
3. `PersonRelationship` table — person ↔ person directed edges with a label. Powers the
   Obsidian-style graph view. Even without a UI, write these when a user says
   "this is Sarah's mom" in the add-person flow.

Relationship strength for graph render: `min(interactionCount / 20, 1f) * 0.6 + (closenessRating / 5f) * 0.4`
(rough formula; tune when you build the view).

---

## Monica feature gap checklist

- [x] Full contact support (import/export vCard, CSV, Android native)
- [x] Organize contacts, photos
- [x] Relationship tracking with custom labels
- [x] Premade + custom life event types
- [x] Premade + custom interaction types
- [x] Activity log (title + body + participant tagging)
- [x] Notes section (per-person and standalone)
- [x] Linking events/activities to multiple people
- [x] Semantic embedding search (Snowflake Arctic XS, offline)
- [x] Offline LLM (Gemma 3 1B LiteRT-LM)
- [x] Gift tracker
- [x] Tasks (per-person + standalone)
- [x] "How we met" / introduction chain (PersonRelationship)
- [x] Everything encrypted (SQLCipher + Android Keystore)
- [x] Android Auto Backup (enable in manifest: `android:allowBackup="true"`, `android:fullBackupContent`)
- [ ] Relationship web graph view — deferred, data captured from day 1
- [ ] Gemini Nano Prompt API (API 35+) — deferred future release
- [ ] Document storage — out of scope

---

## Gradle dependencies to add (beyond standard Compose/Room/Coroutines)

```kotlin
// Encryption
implementation("net.zetetic:android-database-sqlcipher:4.5.4")
implementation("androidx.sqlite:sqlite-ktx:2.4.0")
implementation("androidx.security:security-crypto:1.1.0-alpha06")

// Biometric
implementation("androidx.biometric:biometric:1.1.0")

// Embedding (TFLite)
implementation("com.google.ai.edge.litert:litert:1.0.1")

// LLM (LiteRT-LM) — check latest version on Maven
implementation("com.google.ai.edge.litertlm:litertlm-android:0.10.0")

// Contact import
// EZVCard for vCard parsing: "ez-vcard:ez-vcard:0.11.3"

// WorkManager for background model download
implementation("androidx.work:work-runtime-ktx:2.9.0")

// DataStore for settings
implementation("androidx.datastore:datastore-preferences:1.1.1")
```

---

## Manifest requirements (from Lattice, carry forward)

```xml
<!-- For LiteRT-LM NPU on Qualcomm devices -->
<uses-native-library android:name="libcdsprpc.so" android:required="false" />
<uses-native-library android:name="libOpenCL.so" android:required="false" />

<!-- For contact import -->
<uses-permission android:name="android.permission.READ_CONTACTS" />

<!-- For calendar bridge -->
<uses-permission android:name="android.permission.READ_CALENDAR" />
<uses-permission android:name="android.permission.WRITE_CALENDAR" />

<!-- Encrypted backup -->
<application android:allowBackup="true" android:fullBackupContent="@xml/backup_rules" ...>
```
