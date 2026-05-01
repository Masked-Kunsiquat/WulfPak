## Commands

```bash
# Compile-check (fast — use this before committing)
./gradlew :app:compileDebugKotlin

# Full debug build
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Ad-hoc Python scripts (python3 is not on PATH — use uv)
uv run python script.py
```

## Module layout

| Module | Contents |
|--------|----------|
| `:core-data` | Room entities/DAOs, `AppDatabase`, `KeyProvider`, type converters |
| `:core-logic` | `FamilyInferenceEngine`, `LlmOrchestrator`, `ContactsToolSet`, `EmbeddingProvider`, `SearchRepository`, `EmbeddingWorker` |
| `:app` | Compose UI, Navigation, `AppApplication`, `BiometricGate`, sync/download workers |

## Key files

- `app/AppApplication.kt` — all lazy singletons (`db`, `familyInferenceEngine`, `llmOrchestrator`, etc.)
- `core-data/AppDatabase.kt` — Room v4, migration history, schema exported to `core-data/schemas/`
- `app/navigation/AppNavigation.kt` — all routes and Compose NavHost
- `core-logic/llm/ContactsToolSet.kt` — all 17 LLM tools
- `core-logic/llm/Prompts.kt` — all system prompt strings

## Architecture

- **No Hilt.** DI is manual: `AppApplication` holds lazy singletons; ViewModels access them via `getApplication<AppApplication>()`.
- **Room DB is at version 4.** Migrations live in `AppDatabase.kt`; schema JSON files in `core-data/schemas/`.
- **LLM model:** Gemma 4 E4B via LiteRT-LM (`litertlm 0.10.0`). Tool descriptions must be ≤ 180 chars — longer strings silently break all tools.
- **Encryption:** SQLCipher 4.5.4 via `SupportFactory`; key in Android Keystore via `EncryptedSharedPreferences`.

## Critical gotchas

- **Never add `alias(libs.plugins.kotlin.android)` to `:core-data` or `:core-logic` build files.** AGP 9.x auto-applies KGP to Android library modules — adding it manually causes a "Cannot add extension with name 'kotlin'" crash at sync time.
- **`PersonRelationship` label is stored from personA's perspective** (lower UUID = personA). `PersonConnection.effectiveLabel` handles the reversal for display. `addConnection()` normalises asymmetric family labels before insert.
- **`@Tool` methods in `ContactsToolSet` run on LiteRT's thread pool** — `runBlocking {}` is intentional and safe there.
