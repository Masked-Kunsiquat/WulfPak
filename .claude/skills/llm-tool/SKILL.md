---
name: llm-tool
description: Scaffold a new @Tool method in WulfPak's ContactsToolSet. Enforces the 180-char description limit, matches existing code patterns, and wires the tool into the system prompt.
---

You are adding a new LLM tool to `core-logic/src/main/java/com/github/maskedkunisquat/wulfpak/core/logic/llm/ContactsToolSet.kt`.

## Step 1 — Understand the request

Ask (if not already specified):
1. What should the tool do?
2. What parameters does it need?
3. Should it be read-only or write a staged write (like `logInteraction` pattern)?

## Step 2 — Read the file

Read `ContactsToolSet.kt` to understand:
- The existing tool list (so the new tool doesn't duplicate one)
- The `stagedWrites` pattern if this is a write tool
- The `runBlocking {}` pattern (intentional — LiteRT thread pool, safe here)

## Step 3 — Draft the method

Follow the exact existing pattern:

```kotlin
@Tool(description = "<description — MUST be ≤ 180 chars>")
fun myNewTool(
    @ToolParam(description = "<param description>") param: String,
): String = runBlocking {
    // implementation
}
```

**Before finalising the description string, count its characters.** The limit is **180 characters**. Longer descriptions silently break ALL tools — not just this one. If the draft exceeds 180 chars, shorten it until it fits.

## Step 4 — Update the system prompt

Read `core-logic/src/main/java/com/github/maskedkunisquat/wulfpak/core/logic/llm/Prompts.kt` and add a line to the tool-guidance section describing when the LLM should invoke the new tool.

## Step 5 — Verify

After editing, count the final description length and confirm it is ≤ 180 characters. Show the count to the user.

## Existing tools (do not duplicate)

`getContactNotes`, `getContactGifts`, `getContactHistory`, `getPendingTasks`, `getContactDetails`, `searchAcrossContacts`, `getUpcomingEvents`, `getLapsedContacts`, `findContactsByRelation`, `getLifeEvents`, `getRelationshipWeb`, `inferKinship`, `inferRelationBetween` — plus staged-write tools for logging interactions, adding notes/tasks/gifts.