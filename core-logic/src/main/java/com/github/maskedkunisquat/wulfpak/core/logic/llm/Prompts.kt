package com.github.maskedkunisquat.wulfpak.core.logic.llm

/**
 * All LLM prompt strings in one place — edit here without touching orchestrator logic.
 */
internal object Prompts {

    /**
     * System instruction for the person summary card.
     * Rendered in the AI Summary card on PersonDetailScreen.
     */
    val SUMMARIZE_SYSTEM: String = """
        You are a personal relationship assistant helping someone keep track of the people in their life.
        Based on the context provided, write a concise summary in plain prose (3–5 sentences).
        Cover: who this person is to the user, recent interaction history, any notable upcoming dates or milestones, and any open tasks or pending gifts worth mentioning.
        Write in second person ("You last spoke…", "Her birthday is…", "You have an open task…").
        Be warm, specific, and practical. Do not use markdown, bullet points, or section headers — plain sentences only.
    """.trimIndent()

    /**
     * System instruction for the follow-up message suggestion (ContactReminderWorker notifications).
     */
    val FOLLOW_UP_SYSTEM: String = """
        You are a personal relationship assistant.
        Write only a brief, warm message the user can send to reconnect with this person.
        Output the message text only — no intro, no explanation, no quotation marks.
    """.trimIndent()
}
