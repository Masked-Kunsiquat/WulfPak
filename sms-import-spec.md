# SMS Text Import — Spec Sheet

Import SMS conversations into WulfPak interactions via a copy/paste flow, with sender-attribution UI and ML Kit GenAI Summarization for note generation. Scoped 2026-05-04.

**Share target is not viable.** Google Messages does not surface share options when selecting a thread. Samsung Messages is being discontinued. The entry mechanism is the Android clipboard: the user multi-selects message bubbles in Google Messages, copies, and pastes into WulfPak.

---

## Clipboard format (confirmed)

Multi-selecting and copying in Google Messages produces plain newline-separated text with no timestamps, sender names, or delimiters:

```
hey!
hey, how's your day going?
pretty good, just relaxing
what about you?
```

**Known limitation — newlines:** A message that itself contains newlines will appear as multiple lines — indistinguishable from separate messages. Accepted as a v1 constraint; most texts don't contain internal newlines in practice.

**Known limitation — selection order:** Google Messages pastes messages in the order the user tapped them, not chronological order. If the user selects bubbles out of order, the paste will be out of order. The app cannot detect or correct this — there are no timestamps in the clipboard payload. Mitigated by a persistent inline banner on `SmsImportScreen`: "Messages appear in the order you selected them — select top-to-bottom for chronological order."

---

## Proposed pipeline

```text
User copies messages in Google Messages
    ↓
Opens WulfPak → Interactions tab FAB → "Paste conversation" → SmsImportScreen
    ↓
Pastes raw text; app splits on newlines → line list
    ↓
User assigns each line to a sender (toggle: Me ↔ Contact)
    ↓
User sets conversation date (defaults to today; optional back-date via date picker)
    ↓
Labeled lines formatted as "Name: message" → ML Kit Summarize (or Gemma fallback)
    ↓
Summary pre-fills note draft; user edits
    ↓
Confirm → Interaction(type = TEXT) + participant + onInteractionAdded + Note written to DB
```

---

## Phases

### Phase 1 — SMS Import entry point + paste screen

- [ ] Add `Routes.SMS_IMPORT = "sms_import/{personId}"` to `AppNavigation`; contact is required at entry — no contact-agnostic entry path
- [ ] Entry point: two-step FAB on the **Interactions tab** of `PersonDetailScreen` — tap FAB → bottom sheet / menu with "Manual log" and "Paste conversation"; tapping "Paste conversation" navigates to `SmsImportScreen`
- [ ] `SmsImportScreen` composable: persistent info banner at the top — "Messages appear in the order you selected them — select top-to-bottom for chronological order"; single `OutlinedTextField` (multiline, full-width) labelled "Paste messages here"; **Import** button parses content on tap
- [ ] Split pasted text on `\n`; filter blank lines; produce `List<String>` — each string is one candidate message
- [ ] Navigate to attribution screen with the line list + `personId`

### Phase 2 — Sender attribution UI

- [ ] `SmsAttributionScreen`: `LazyColumn` of message rows, each showing the text and a sender toggle chip
- [ ] Toggle chip: two states — **Me** / **{Contact firstName}**; tapping flips the attribution for that row
- [ ] Default attribution: Contact first (they typically send first in conversations you'd bother to log)
- [ ] "Flip all below" action on each row to batch-flip the remainder from that point
- [ ] Date picker row at the top: defaults to today; user can back-date
- [ ] **Next** button proceeds to summarization + confirm

### Phase 3 — ML Kit GenAI Summarization

- [ ] Add dependency: `com.google.mlkit:genai-summarization:1.0.0-beta1`
- [ ] Format attributed lines as `"Name: message\n"` for `InputType.CONVERSATION`
- [ ] Call `checkFeatureStatus()` before summarizing:
  - `AVAILABLE` → proceed
  - `DOWNLOADABLE` → trigger download, show progress indicator, retry after
  - `DOWNLOADING` → wait, show progress
  - `UNAVAILABLE` → show one-time banner ("Summarization unavailable on this device — using on-device assistant instead"), then fall through to Gemma path
- [ ] Cap input at ~3000 words before passing (tail strategy — keep most recent lines); show a warning snackbar if the paste was truncated
- [ ] Collect summary (1–3 bullet prose); pre-fill note draft field

### Phase 4 — Fallback: Gemma extraction

- [ ] For `UNAVAILABLE` devices: pass attributed text to `LlmOrchestrator` with a prompt to extract key topic, decisions, and follow-ups as a short note
- [ ] Same input cap applies; no new download required

### Phase 5 — Confirm + DB write

- [ ] Show note draft in an editable `OutlinedTextField`; user can revise before confirming
- [ ] **Confirm**: insert `Interaction(type = InteractionType.TEXT, timestamp = selectedDate)` + `InteractionParticipant` + `personDao.onInteractionAdded` + `Note(body = editedDraft)` — same three-call ordered pattern as call log confirm, wrapped in `db.withTransaction {}`
- [ ] **Discard**: navigates back, nothing written
- [ ] `InteractionType.TEXT` already exists in `Interaction.kt` — no Room migration needed

---

## Deferred: Native SMS Provider Import

Supersedes the clipboard flow if implemented. Requires `READ_SMS` — a normal dangerous permission blocked only by Play Store policy (must be default SMS handler). Since WulfPak is sideloaded, the OS grants it on user tap, same as `READ_CALL_LOG`.

### Why it's better than clipboard

| | Clipboard (v1) | SMS provider |
|---|---|---|
| Timestamps | None — need date picker | Real ms timestamps per message |
| Attribution | Manual toggle per row | `type` column: 1=received 2=sent — automatic |
| Order | User's tap order (fragile) | Chronological by `date` column |
| Newline ambiguity | Unresolvable | Non-issue (`body` is a single field) |
| Entry UX | Copy in Messages → paste | Stay in WulfPak, pick a thread |

### Gating

Clipboard flow works in both flavors — it uses no restricted permissions. The native SMS provider flow requires `READ_SMS`, which violates Play Store policy (must be default SMS handler). Gate it behind a `full` build flavor (the sideload edition) so the clipboard path compiles cleanly in a future `play` flavor without `#ifdef` noise.

| Flavor | Clipboard import | Native SMS import |
|---|---|---|
| `full` (sideload) | ✓ | ✓ |
| `play` (if ever published) | ✓ | — |

MVP ships clipboard only. Native flow lands later in `full` without touching the clipboard implementation.

### Phase 6 — Thread picker

- [ ] `SmsThreadPickerScreen`: queries `content://sms/conversations` + joins to `content://sms/` to resolve the most recent sender address per thread; shows a list of threads sorted by recency
- [ ] Match each thread's address against WulfPak contacts via `PhoneUtils.normalizePhone` — surface matched contacts at the top; unmatched threads listed below with raw number
- [ ] Tapping a thread navigates to the message window screen (Phase 7) with `threadId` + matched `personId` (nullable)

### Phase 7 — Message window selection

- [ ] `SmsMessageWindowScreen`: shows all messages in the thread as a read-only `LazyColumn`, newest at bottom
- [ ] Two selection modes (decide at implementation time — either works):
  - **Date range** — "From" / "To" date pickers; messages outside the range are dimmed
  - **Tap-to-anchor** — tap a message to set start; shift-tap (or long-press) to set end; selected range highlighted
- [ ] Show message count and estimated token load for the selected window; warn if over the ~3000-word cap (same tail truncation logic as clipboard flow)
- [ ] **Import** button passes the selected `List<SmsMessage>` forward — each already has `body`, `date`, and `type` (sent/received), so attribution and timestamps are resolved automatically
- [ ] No attribution screen needed; no date picker needed; pipeline resumes at summarization (Phase 3 / Phase 4)

### Entry point change (when native flow ships)

Replace the "Paste conversation" FAB option with "Import from SMS" — or offer both if clipboard fallback is still desired for edge cases (e.g. iMessage threads forwarded via another app).

---

## Decisions (resolved 2026-05-04)

| # | Question | Decision |
|---|----------|----------|
| Q1 | Entry point on PersonDetailScreen | Two-step FAB on the Interactions tab — tap FAB → two options: "Manual log" / "Paste conversation" |
| Q2 | Conversation date | Default today + optional date picker the user can back-date |
| Q3 | Default sender | Contact first |
| Q4 | ML Kit UNAVAILABLE UX | One-time banner ("Summarization unavailable — using on-device assistant instead"), then silent Gemma fallback |
| Q5 | Long paste truncation | Tail strategy (keep most recent lines) + show a warning snackbar/banner when the cap is exceeded |
| Q6 | `InteractionType.TEXT` | Already defined in `Interaction.kt` — no Room migration needed |
| Q7 | Beta API risk | Accept `1.0.0-beta1`, pin version, monitor release notes |

---

## Compile checkpoints

```bash
./gradlew :app:compileDebugKotlin   # after each phase
./gradlew assembleDebug && ./gradlew installDebug   # final
```

---

## Manual test checklist

- [ ] "Paste conversation" entry is reachable via the Interactions tab FAB
- [ ] Selection-order banner is visible on `SmsImportScreen`
- [ ] Pasting a copied Google Messages selection splits correctly into lines
- [ ] Blank lines are filtered out
- [ ] Sender toggle flips correctly per-row; "Flip all below" works
- [ ] Date picker back-dating produces correct `Interaction.timestamp`
- [ ] ML Kit summary appears pre-filled in the note field
- [ ] User can edit the note before confirming
- [ ] Confirm writes `Interaction(type = TEXT)` + participant + note; interaction appears on person screen
- [ ] `lastContactedAt` updates to the selected date (not today if back-dated)
- [ ] Discard writes nothing
- [ ] Fallback to Gemma works on a device where ML Kit is `UNAVAILABLE`
- [ ] A paste with internal newlines in a message degrades gracefully (extra lines, not a crash)
- [ ] Pasting a very long thread truncates to the cap without crashing
