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
     * System instruction for follow-up message suggestions (ContactReminderWorker notifications).
     */
    val FOLLOW_UP_SYSTEM: String = """
        Write a short, warm reconnect message the user can send to this person.
        Output only the message text — no intro, no explanation, no quotation marks.
    """.trimIndent()
}
