# SMS Text Import — Spec Sheet

Import SMS conversations into WulfPak interactions via Android's Share target mechanism, with ML Kit GenAI Summarization for note generation. Scoped 2026-05-04.

---

## Feature overview

The user selects a text thread in their native SMS app (Google Messages, Samsung Messages, etc.), taps **Share**, and chooses WulfPak from the share sheet. WulfPak receives the raw conversation text, identifies the contact, runs ML Kit Summarize to produce a note body, then drops the result into the existing pending-call confirm queue for review before writing to the DB.

**No new background permissions required.** This is entirely user-initiated — the user consciously leaves their SMS app and picks WulfPak from the share sheet.

---

## Proposed pipeline

```
User selects thread → Share sheet → WulfPak ShareReceiver Activity
    ↓
Parse Intent extras (EXTRA_TEXT / EXTRA_STREAM)
    ↓
Attempt contact match (phone number in thread header, or first line of text)
    ↓
ML Kit Summarize (InputType.CONVERSATION, "Name: message" format)
    ↓
PendingCallStub-equivalent for SMS (type = "SMS", note pre-filled with summary)
    ↓
Existing PendingCallsScreen confirm flow
    ↓
Interaction + Note written to DB on confirm
```

---

## Phases

### Phase 1 — Share target registration

- [ ] Add `<activity android:name=".ui.share.ShareReceiverActivity">` to `AndroidManifest.xml`
- [ ] Intent filter: `action=ACTION_SEND`, `mimeType=text/plain`, `category=DEFAULT + BROWSABLE`
- [ ] `ShareReceiverActivity` receives `Intent.EXTRA_TEXT`, passes it to a ViewModel, then navigates into the existing Compose NavHost (or is itself a transparent trampoline that starts `MainActivity` with extras)
- [ ] Decide: trampoline activity vs. direct Compose integration (see Open Q #1)

### Phase 2 — Contact resolution

- [ ] Parse phone number from the share payload — likely in the thread title/subject line or first sender token (format varies by app; see Open Q #2)
- [ ] Call existing `PhoneUtils.normalizePhone` + `ContactsToolSet.findPerson()` phone-fallback path
- [ ] If no match: show contact-picker UI rather than silently dropping (unlike call log, where drops are silent — the user initiated this share intentionally)
- [ ] If match: proceed to summarization

### Phase 3 — ML Kit GenAI Summarization

- [ ] Add dependency: `com.google.mlkit:genai-summarization:1.0.0-beta1`
- [ ] On feature entry: call `Summarization.getClient(SummarizationOptions(InputType.CONVERSATION))`
- [ ] Call `checkFeatureStatus()` before any summarization attempt:
  - `AVAILABLE` → proceed
  - `DOWNLOADABLE` → trigger download, show progress, retry after
  - `DOWNLOADING` → wait, show progress
  - `UNAVAILABLE` → fall through to Gemma path (see Open Q #3)
- [ ] Format input: prefix each line with `"Name: message"` where Name is sender (parsed from thread) — required for `CONVERSATION` input type
- [ ] Cap input at ~4000 tokens / ~3000 words before passing to summarizer (see Open Q #4)
- [ ] Collect summary (1–3 bullet prose); use as pre-filled note body in the confirm UI

### Phase 4 — Fallback: Gemma structured extraction

- [ ] For devices where ML Kit Summarize is `UNAVAILABLE` (unlocked bootloader, unsupported chipset):
  - Pass raw thread text to `LlmOrchestrator` with a targeted prompt
  - Prompt: extract the key topic, decisions, and follow-ups as a short note
  - Cap input to avoid overwhelming the context window (see Open Q #4)
- [ ] Gemma is already on-device — no new download required for fallback path

### Phase 5 — Confirm queue integration

- [ ] Define `PendingSmsStub` data class (or extend `PendingCallStub` with `type = "SMS"` and a nullable `noteDraft: String` field — see Open Q #5):
  ```kotlin
  data class PendingSmsStub(
      val personId: String,
      val personFirstName: String,
      val timestamp: Long,       // time of share action (not thread timestamp)
      val noteDraft: String,     // ML Kit summary or Gemma extraction
      val rawText: String,       // truncated original, for user review
  )
  ```
- [ ] Add `PENDING_SMS_STUBS` to `AppPrefsKeys` (same JSON-array DataStore pattern)
- [ ] Reuse or extend `PendingCallsScreen` / `PendingCallsViewModel` with SMS cards (see Open Q #5)
- [ ] Confirm path: insert `Interaction(type = InteractionType.TEXT)` + participant + `onInteractionAdded` + `Note(body = editedDraft)`
- [ ] Skip path: remove stub from DataStore

---

## Open questions

### Q1 — Activity architecture: trampoline vs. Compose integration
The share intent lands on an `Activity`. WulfPak's UI is a single-Activity Compose app. Options:
- **Trampoline**: `ShareReceiverActivity` is a no-UI activity that re-fires `MainActivity` with extras and finishes. Simple but adds a navigation hop.
- **Direct**: `ShareReceiverActivity` hosts its own Compose content (a bottom sheet or dialog). Cleaner UX but means a second `setContent {}`.
- **NavController deep-link**: register a deep-link on the pending-SMS route so `MainActivity` handles the intent directly.

Which pattern fits best with the existing single-Activity nav setup?

### Q2 — Share payload format across SMS apps
What does `Intent.EXTRA_TEXT` actually contain when sharing from:
- **Google Messages**: does it include sender name/number in the string, or just message bodies?
- **Samsung Messages**: same question
- **Carrier/OEM apps**: unknown

The contact-match strategy depends entirely on this. Need to test on a real device before committing to a parsing approach. May need to special-case per-app format or ask the user to confirm the match manually.

### Q3 — ML Kit unavailability fallback UX
`UNAVAILABLE` can't be fixed (locked to device/bootloader state). Options:
- Silent fallback to Gemma (user never knows)
- Show a one-time banner: "Summarization not available on this device — using on-device assistant instead"
- Skip summarization entirely and show a blank note field with just the raw text

What's the right UX for a degraded experience?

### Q4 — Long thread truncation strategy
ML Kit's `CONVERSATION` input has a ~4000-token limit. Long threads (weeks of messages) will exceed it. Options:
- Truncate to most-recent N messages (tail strategy — most relevant for recency)
- Truncate to first N messages (head strategy — preserves context)
- Summarize in chunks and concatenate (complex, probably overkill)
- Let the user trim before sharing (shifts burden to user)

Tail strategy (most recent messages) is the intuitive default, but need to decide the cutoff (number of lines, character count, or token estimate).

### Q5 — Reuse `PendingCallStub` or define `PendingSmsStub`
Two approaches:
- **Extend**: add `type: String` and `noteDraft: String?` to the existing `PendingCallStub`. Reuses all DataStore + VM + UI logic. May feel like a leaking abstraction.
- **Separate**: new `PendingSmsStub` + `PENDING_SMS_STUBS` key + separate VM methods. Cleaner model boundaries, more code.

The confirm UI differs enough (SMS shows note draft, no duration/call-type chip) that a separate card composable is needed either way. The question is whether to share the underlying queue infrastructure.

### Q6 — Beta API risk tolerance
ML Kit GenAI Summarization is `1.0.0-beta1` with no SLA and possible breaking changes. This is the same posture as any other beta Android library, but the model download (Gemini Nano via AICore) adds a network dependency at first use.

Is this acceptable for a personal/hobby app, or should we wait for stable?

### Q7 — Interaction timestamp semantics
For call log import, `timestamp` is the actual call time (from `CallLog.Calls.DATE`). For SMS import, what timestamp should the `Interaction` carry?
- Time of the share action (now) — simple but loses recency info
- Timestamp of the most recent message in the thread — accurate but requires parsing
- Timestamp of the oldest message in the thread — represents when the conversation started

This affects `lastContactedAt` accuracy and interaction sort order.

### Q8 — `InteractionType` extension
`InteractionType.CALL` already exists. Does `InteractionType.TEXT` exist, or does it need to be added (Room migration required)?

Check `core-data` entities before starting Phase 5.

---

## Compile checkpoints

```bash
./gradlew :app:compileDebugKotlin   # after each phase
./gradlew assembleDebug && ./gradlew installDebug   # final
```

---

## Manual test checklist

- [ ] WulfPak appears in the share sheet when sharing from Google Messages
- [ ] WulfPak appears in the share sheet when sharing from Samsung Messages (if applicable)
- [ ] Contact match succeeds for a known contact's thread
- [ ] Contact match failure shows picker UI rather than silently dropping
- [ ] ML Kit summary appears pre-filled in the note field on the confirm card
- [ ] User can edit the pre-filled note before confirming
- [ ] Confirm writes `Interaction(type = TEXT)` + participant + note to DB
- [ ] Confirmed interaction appears on the person's detail screen
- [ ] Skip removes the stub without writing anything
- [ ] Fallback to Gemma works on a device where ML Kit is `UNAVAILABLE`
- [ ] Long threads are truncated without crashing
