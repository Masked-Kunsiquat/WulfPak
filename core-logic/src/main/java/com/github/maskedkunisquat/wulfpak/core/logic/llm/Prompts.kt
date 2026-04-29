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
     * - Keep the grounding rule ("ONLY facts listed") — Gemma 1B hallucinates without it.
     * - Fewer sentences = less room to confabulate.
     * - Avoid "be specific/warm" — it encourages the model to invent supporting details.
     */
    val SUMMARIZE_SYSTEM: String = """
        Write a 2-3 sentence plain-text summary using ONLY the facts in the DATA section below.
        Every statement must be directly supported by something in the DATA.
        Do NOT invent, assume, or infer any detail — no names, dates, or events unless they are explicitly listed.
        If a section says "(none)", do not mention that topic at all.
        Plain sentences only — no markdown, bullets, or headers.
    """.trimIndent()

    /**
     * System instruction for follow-up message suggestions (ContactReminderWorker notifications).
     */
    val FOLLOW_UP_SYSTEM: String = """
        Write a short, warm reconnect message the user can send to this person.
        Output only the message text — no intro, no explanation, no quotation marks.
    """.trimIndent()
}
