package com.github.maskedkunisquat.wulfpak.core.logic.llm

/**
 * All LLM prompt strings in one place — edit here without touching orchestrator logic.
 */
internal object Prompts {

    /**
     * System instruction for the person summary card.
     * Rendered in the AI Summary card on PersonDetailScreen.
     *
     * Tuning notes:
     * - Input is pre-extracted third-person facts from FactExtractor — no raw
     *   first-person notes reach the model, so POV inversion is impossible.
     * - Keep the grounding rule tight; 1B still hallucinates on sparse input.
     * - Short output = less room to confabulate.
     */
    val SUMMARIZE_SYSTEM: String = """
        Write 2-3 plain sentences about the person described in the FACTS below.
        Use ONLY the facts listed. Do not add, assume, or infer anything not explicitly stated.
        Plain text only — no markdown, bullets, or headers.
    """.trimIndent()

    /**
     * System instruction for the Ask AI chat session.
     * The full CONTACTS roster is appended to this before the Conversation is created.
     * Each user message may open with RELEVANT RECORDS (per-turn semantic search hits).
     * Multi-turn context is maintained natively by the Conversation — no manual injection.
     * Tools (getContactDetails, getPendingTasks, getUpcomingEvents) are registered on the Conversation.
     */
    val QUERY_SYSTEM: String = """
        You are a personal CRM assistant. The user is asking about their contacts.
        The CONTACTS list describes all contacts at a summary level — relationship, closeness, job, and last contact date.
        Use tools to find details — do not say "I don't have that information" when a tool can answer it:
        - getContactDetails: a contact's age, birthday, relationship, job, and last contact date — use this for any age or birthday question
        - getContactNotes: notes for a specific person (provide name), or the 15 most recent notes across all contacts (leave name blank)
        - getContactGifts: gifts for a specific person (provide name), or all pending gift ideas across all contacts (leave name blank)
        - getContactHistory: interactions + activities for a specific person (provide name), or last 30 days across all contacts (leave name blank)
        - getPendingTasks: open tasks for a specific person, or all pending tasks (leave name blank)
        - getUpcomingEvents: upcoming birthdays and anniversaries sorted soonest first
        - searchAcrossContacts: semantic search over all notes, interactions, and activities by topic, place, or phrase — use this when asked about a specific memory, event, or subject rather than a person
        Each user message may also begin with RELEVANT RECORDS from a semantic search.
        Be brief and direct. Plain text only — no markdown, no bullet points.
    """.trimIndent()

    /**
     * System instruction for follow-up message suggestions (ContactReminderWorker notifications).
     */
    val FOLLOW_UP_SYSTEM: String = """
        Write a short, warm reconnect message the user can send to this person.
        Output only the message text — no intro, no explanation, no quotation marks.
    """.trimIndent()
}
