# ui/pendingcalls package

`PendingCallsScreen` and `PendingCallsViewModel`. Handles the review-and-confirm flow for call log stubs imported by `CallLogImportWorker`.

## Stub lifecycle

```
CallLogImportWorker
    → writes PendingCallStub list to PENDING_CALL_STUBS DataStore key (JSON)

PendingCallsViewModel.pendingStubs (StateFlow)
    → deserialised from DataStore on each update

PendingCallsScreen
    → badge on People tab when pendingStubs.isNotEmpty()
    → navigates to PENDING_CALLS route on badge tap

User taps Confirm
    → confirm(stub): DB transaction (Interaction + InteractionParticipant + onInteractionAdded)
    → DataStore write removes stub
    → stub moves to confirmedStubs (Compose state, shown in secondary-container card)

User sees note prompt on confirmed card
    → saveNote(stub, text):
        - if text blank: dismissConfirmed immediately (no DB write)
        - if text non-blank: insert Note, then dismissConfirmed on success
        - on DB failure: silent catch, card stays visible for retry

User taps Skip
    → skip(stub): DataStore write removes stub, no DB write

User taps Ask assistant (on confirmed card)
    → navigates to SEARCH with seed prompt:
      "I just confirmed a {callType.lowercase()} with {firstName} from {date}. Want to add a note?"
```

## Screen layout

**PendingCallCard** (unconfirmed stubs):
- Standard card, no special colouring
- Shows: person name, call type chip, duration (`m:ss` or `—`), date
- Buttons: Confirm / Skip

**ConfirmedCallCard** (confirmed stubs):
- Container colour: `secondaryContainer`
- CallTypeChip colour: `secondaryContainer` (normal) or `errorContainer` (MISSED)
- Shows: person name, call type chip, duration, date
- `OutlinedTextField` for note (minLines = 2)
- Buttons: Save note / Dismiss
- `AssistChip`: Ask assistant (navigates to SEARCH)

## ViewModel state

| Property | Type | Source |
|----------|------|--------|
| `pendingStubs` | `StateFlow<List<PendingCallStub>>` | DataStore, WhileSubscribed(5000) |
| `confirmedStubs` | `List<PendingCallStub>` (Compose state) | In-memory only; lost on process death |

## Non-atomicity note

`confirm()` performs a DB transaction and a separate DataStore write — they are not atomic. If the process dies between the two, the stub reappears on next launch but the Interaction is already in the DB. Re-confirming creates a duplicate Interaction. Acceptable for a personal-use app; the failure window is extremely narrow.

## PendingCallStub validation

`toPendingCallStubs()` (in `PendingCallStub.kt`) validates `personId` as a parseable UUID via `UUID.fromString()` and drops the stub if invalid. This prevents `confirm()` and `saveNote()` from crashing on malformed DataStore data. Do not weaken this guard.
