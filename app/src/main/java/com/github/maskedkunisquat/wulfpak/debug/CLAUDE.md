# debug package (app)

`DebugEventLogger` — NDJSON storage and summary generation. The `DebugEvent` sealed class lives in `core-logic/src/main/java/.../core/logic/debug/DebugEvent.kt`.

## DebugEvent types

All subtypes include `ts: Long` (epoch ms) and `sessionId: String?`.

| Type | Key fields | Logged by |
|------|-----------|-----------|
| `LlmQuery` | `durationMs`, `toolCallCount`, `toolsUsed: List<String>`, `error: String?` | `LlmOrchestrator.query()` |
| `ToolCall` | `tool: String`, `argKeys: List<String>`, `success: Boolean` | `ContactsToolSet` tool methods |
| `LlmSummarize` | `subject: String`, `durationMs`, `success: Boolean` | `LlmOrchestrator.summarize()` |
| `EmbeddingRun` | `notesEmbedded`, `interactionsEmbedded`, `activitiesEmbedded`, `failed: Boolean`, `durationMs`, `result: String` | `EmbeddingWorker` |
| `SearchQuery` | `queryLen`, `candidates`, `results`, `durationMs`, `zeroVector: Boolean` | `SearchRepository` |
| `Backup` | `op: String`, `recordCount`, `durationMs`, `success: Boolean`, `error: String?` | `BackupRepository` |
| `Nav` | `to: String`, `from: String?` | `AppNavigation` destination listener |
| `Biometric` | `result: String` | `BiometricGate` |
| `CallLogImport` | `stubsFound`, `stubsAdded`, `durationMs` | `CallLogImportWorker` |
| `PendingCallAction` | `action: String` ("CONFIRM"\|"SKIP"), `callType: String` ("INCOMING"\|"OUTGOING"\|"MISSED") | `PendingCallsViewModel` |

## Adding a new DebugEvent

1. Add a new `data class` subclass to `DebugEvent` sealed class in core-logic
2. Log it via `debugEventLogger.log(DebugEvent.MyEvent(...))` at the relevant call site
3. Add a serialization branch in `DebugEventLogger.serialize(event)` 
4. Add a parsing + summary section in `generateTextSummary()` for the new type

## DebugEventLogger storage

- **File**: `context.filesDir / "debug_events.ndjson"` — one JSON object per line
- **Backup file**: `"debug_events.bak.ndjson"` — previous log after rotation
- **Max size**: 5 MB; when exceeded, current log is renamed to bak and a fresh log starts
- **Format per line**: `{"id":"uuid","ts":ms,"session":"...","v":1,"type":"EventTypeName","payload":{...}}`
- **`captureEnabled`**: `@Volatile Boolean`; when false, `log()` is a no-op; toggled in real time by `AppApplication` observing `DEBUG_CAPTURE_ENABLED` DataStore key
- **`totalCount()`**: synchronized; counts non-blank lines in both bak + log files

## generateTextSummary()

Parses all events from both files; groups by type; builds a plain-text ASCII report with sections for each event type (only sections with data are shown):

- **LLM_QUERY** — avg/max duration, avg/max tools per query, top 8 tools by call count
- **LLM_SUMMARIZE** — avg duration, success rate
- **EMBEDDING_RUN** — avg items, avg duration, result distribution
- **SEARCH_QUERY** — avg duration, avg results, zero-vector count (model not ready)
- **BACKUP** — per-op: record count, duration, success/failure
- **NAV** — top 8 transitions (`from → to`, count)
- **CALL_LOG_IMPORT** — total found/added/deduped, avg/max duration
- **PENDING_CALL_ACTION** — confirm vs skip count, by call type breakdown
- **BIOMETRIC** — result distribution
- **FLAGS** — warnings: queries > 10 s, embedding retries, zero-vector searches, failed backups

## exportToUri(context, uri)

Reads all events from both files; writes a single JSON object to the content resolver URI:
```json
{"exported_at":"…","event_count":N,"events":[…]}
```
Used by `DebugSummaryScreen` share button.
