# Monice Dopple — Init Roadmap

## Phase 0 — Project scaffolding
- [x] Add Kotlin + KSP + Compose plugins to root `build.gradle.kts`
- [x] Register `:core-data` and `:core-logic` in `settings.gradle.kts`
- [x] Create `core-data/` module skeleton with `build.gradle.kts`
- [x] Create `core-logic/` module skeleton with `build.gradle.kts`
- [x] Populate `libs.versions.toml` with all deps (SQLCipher, Room, LiteRT, LiteRT-LM, WorkManager, DataStore, Biometric, EZVCard)
- [x] Update `:app` `build.gradle.kts` for Kotlin + Compose + module deps

## Phase 1 — `:core-data` (Room + SQLCipher schema)
- [ ] Copy `monice-dopple/db/` into `:core-data`, rename package to `com.github.maskedkunisquat.wulfpak`
- [ ] Write `Person` entity + DAO
- [ ] Write `ContactDetail` entity + DAO
- [ ] Write `LifeEvent` entity + DAO
- [ ] Write `Interaction` + `InteractionParticipant` entities + DAOs
- [ ] Write `Activity` + `ActivityParticipant` entities + DAOs
- [ ] Write `Note` entity + DAO
- [ ] Write `Gift` entity + DAO
- [ ] Write `Task` entity + DAO
- [ ] Write `PersonRelationship` entity + DAO
- [ ] Write `AppDatabase` (Room + SQLCipher, registers `AppTypeConverters`)
- [ ] Wire `KeyProvider` into `AppDatabase` open helper

## Phase 2 — `:core-logic` (embeddings + search + LLM)
- [ ] Copy `monice-dopple/embedding/` into `:core-logic`, rename package
- [ ] Copy `monice-dopple/search/` into `:core-logic`, rename package
- [ ] Copy `monice-dopple/llm/` into `:core-logic`, rename package
- [ ] Write `SearchRepository` (cosine search over Note, Interaction, Activity embeddings)
- [ ] Write `LlmOrchestrator` (summarize history, NL query, follow-up suggestion)
- [ ] Write WorkManager embedding worker (embed on write, store 384-dim BLOB)

## Phase 3 — `:app` scaffolding + biometric gate
- [ ] Copy `monice-dopple/biometric/BiometricGate.kt` into `:app`, rename package
- [ ] Write `AppApplication` singleton (lazy-init DB, embedding provider, LLM)
- [ ] Set up Compose Navigation graph skeleton (destinations, no screens yet)
- [ ] Wire `BiometricGate` to re-lock on every `ON_STOP`
- [ ] Update `AndroidManifest.xml` (permissions, native library declarations, backup rules)

## Phase 4 — People list + Person detail
- [ ] `PeopleListScreen` (list, search bar, FAB, favorites)
- [ ] `AddEditPersonScreen` (name, relation label, photo, closeness rating)
- [ ] `PersonDetailScreen` (tab layout: Interactions, Notes, Life Events, Gifts, Tasks)
- [ ] `AddEditInteractionScreen`
- [ ] `AddEditNoteScreen`
- [ ] `AddEditLifeEventScreen`
- [ ] `AddEditGiftScreen`
- [ ] `AddEditTaskScreen`

## Phase 5 — Contact import
- [ ] `ContactSyncManager` (reads `ContactsContract`, deduplicates, creates `Person` rows)
- [ ] EZVCard integration for vCard/CSV import
- [ ] Import entry point in Settings screen

## Phase 6 — Global screens
- [ ] Activity feed screen (reverse-chronological Interactions + Activities)
- [ ] Semantic search screen (query → embed → cosine rank)
- [ ] Tasks screen (standalone + person-linked, due date, done toggle)
- [ ] Settings screen (model download, biometric toggle, import)

## Phase 7 — Calendar bridge
- [ ] `CalendarBridge` (writes `LifeEvent` records as `CalendarContract` events)

## Phase 8 — LLM features
- [ ] Person detail "Summarize" card (`LlmOrchestrator.summarize(personId)`)
- [ ] Global natural language search chip
- [ ] WorkManager daily job for "you haven't contacted X in N days" notifications
