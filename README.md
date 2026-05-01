# WulfPak

A personal relationship manager for Android. WulfPak helps you stay close to the people who matter — tracking interactions, remembering details, and surfacing who you've lost touch with, all on-device with no cloud dependency.

---

## Features

- **Contact management** — relationship labels, closeness ratings, last-contacted tracking, vCard import, contact merging
- **Activity & interaction log** — calls, texts, emails, in-person, video, social media; group activities; notes, life events, gift ideas, tasks
- **AI chat** — ask natural language questions about your relationships ("who have I not talked to in a month?", "what's Sarah's brother's name?") using an on-device LLM with 17 purpose-built tools
- **Semantic search** — embedding-based search across notes and interactions using Snowflake Arctic Embed XS
- **Family inference** — graph-based kinship engine that infers parent/child, sibling, grandparent, step- and half-relations
- **Biometric gate** — fingerprint/face unlock with Android Keystore-backed SQLCipher encryption
- **Reconnect reminders** — AI-generated personalized nudges for lapsed contacts via WorkManager

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| UI | Jetpack Compose + Material 3 |
| Language | Kotlin 2.1.0 |
| Database | Room 4 + SQLCipher 4.5.4 |
| On-device LLM | Gemma 4 E4B via LiteRT-LM 0.10.0 |
| Embeddings | Snowflake Arctic Embed XS (TFLite, 384-dim) |
| Background work | WorkManager |
| Security | AndroidX Biometric + Android Keystore |
| DI | Manual (lazy singletons in `AppApplication`) |

**Min SDK:** 24 (Android 7.0) — **Target SDK:** 36 (Android 15)

---

## Module Layout

```text
:app          Compose UI, navigation, BiometricGate, sync/download workers
:core-data    Room entities + DAOs, AppDatabase (v4), KeyProvider, type converters
:core-logic   LlmOrchestrator, FamilyInferenceEngine, ContactsToolSet (17 LLM tools),
              SearchRepository, EmbeddingProvider, EmbeddingWorker
```

---

## Building

```bash
# Compile check (fast)
./gradlew :app:compileDebugKotlin

# Full debug build
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug
```

> Python scripts in `scripts/` require [uv](https://github.com/astral-sh/uv): `uv run python script.py`

---

## Architecture Notes

- No Hilt — all DI is manual via lazy singletons in `AppApplication`. ViewModels access them via `getApplication<AppApplication>()`.
- Room is at **version 4**. Migrations live in `AppDatabase.kt`; exported schemas are in `core-data/schemas/`.
- LLM tool descriptions must be **≤ 180 characters** — longer strings silently break all tools in LiteRT-LM.
- `PersonRelationship` labels are stored from the perspective of the lower-UUID person. `PersonConnection.effectiveLabel` handles display reversal.

---

## Privacy

All data stays on-device. The LLM runs locally via LiteRT-LM, embeddings are computed locally, and the database is encrypted with SQLCipher using a key stored in the Android Keystore. No analytics, no telemetry, no network access required after initial model download.
