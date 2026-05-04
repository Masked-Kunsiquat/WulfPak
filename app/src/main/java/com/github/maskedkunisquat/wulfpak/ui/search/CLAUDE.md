# ui/search package

`SearchScreen`, `SearchViewModel`, and `ChatMessage`. The conversational AI interface backed by `LlmOrchestrator`.

## ChatMessage sealed class

| Subclass | Fields | Represents |
|----------|--------|------------|
| `User(text)` | `text: String` | User's sent message |
| `Assistant(text, isStreaming)` | `text: String`, `isStreaming: Boolean` | AI response (streaming or complete) |
| `ToolCall(name, args, isExpanded)` | `name: String`, `args: Map<String, String>`, `isExpanded: Boolean` | Tool invocation (expandable chip) |
| `PendingWrite(id, description, state)` | `id: String`, `description: String`, `state: WriteState` | Staged write awaiting confirmation |

**WriteState enum**: `PENDING` → `CONFIRMED` or `CANCELLED`

## Message list invariant

`messages` is a `List<ChatMessage>` in Compose state. The last item is always the current `Assistant` message (streaming or complete). ToolCall and PendingWrite items are inserted **before** the last Assistant message as they arrive — they appear inline above the response text.

## askAi() flow

1. Trim query; bail if blank or `isQuerying`
2. Append `User(text)` + `Assistant("", isStreaming = true)` to messages
3. Collect `llmOrchestrator.query(q)`:
   - `Token` → append to last Assistant's text
   - `Complete` → set last Assistant `isStreaming = false`
   - `Error` → set last Assistant `isStreaming = false`
   - `ToolCall` → insert `ChatMessage.ToolCall` before last Assistant
   - `PendingWrite` → insert `ChatMessage.PendingWrite` before last Assistant
4. Catch all exceptions, set `isStreaming = false`

## Write confirmation

- `confirmPendingWrite(id)`: sets message state → CONFIRMED, launches `executePendingWrite(id)` on IO
- `cancelPendingWrite(id)`: sets message state → CANCELLED, calls `llmOrchestrator.cancelPendingWrite(id)` synchronously
- `toggleToolCall(index)`: flips `isExpanded` on ToolCall at that index

## Session memory extraction

`maybeSaveSessionMemory()` is called in `clearConversation()` and `onCleared()`:
1. Checks messages has both User and non-streaming Assistant items
2. Builds conversation string from **User messages only** (excludes Assistant to prevent hallucinations baking into future context)
3. Truncates to 2000 chars
4. Calls `llmOrchestrator.extractSessionMemory(text)` → one sentence
5. Inserts `SessionMemory(timestamp, summary)` to DB
6. Sets `memorySaved` flag to prevent duplicate saves

## clearConversation()

Calls `maybeSaveSessionMemory()`, then: resets `memorySaved`, cancels streaming job, clears messages + query, calls `llmOrchestrator.resetChat()` on IO thread.

## Suggestion chips

- Combined pool of static suggestions + dynamic ones generated from contacts on ViewModel init
- Displays 3 at a time; rotates every 8 seconds via `rotateSuggestions()`
- Tapping a chip seeds `askAi()` with that text

## Screen layout

- `LazyColumn` for messages — auto-scrolls to bottom on new message
- `ToolCallBubble` — tertiaryContainer, shows human-readable tool label + args, expandable
- `PendingWriteBubble` — three visual states matching WriteState
- User bubbles — right-aligned, primaryContainer
- Assistant bubbles — left-aligned, surfaceVariant; `LinearProgressIndicator` shown when streaming with empty text
- `ModelWarningBanner` — shown when `modelLoadState` is not READY

## isQuerying

Derived from messages: `true` when the last message is `Assistant(isStreaming = true)`. Guards `askAi()` from double-submission.
