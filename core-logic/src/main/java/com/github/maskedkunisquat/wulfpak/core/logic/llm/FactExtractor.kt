package com.github.maskedkunisquat.wulfpak.core.logic.llm

import com.github.maskedkunisquat.wulfpak.core.data.entity.Activity
import com.github.maskedkunisquat.wulfpak.core.data.entity.Gift
import com.github.maskedkunisquat.wulfpak.core.data.entity.GiftStatus
import com.github.maskedkunisquat.wulfpak.core.data.entity.Interaction
import com.github.maskedkunisquat.wulfpak.core.data.entity.LifeEvent
import com.github.maskedkunisquat.wulfpak.core.data.entity.Note
import com.github.maskedkunisquat.wulfpak.core.data.entity.Person
import com.github.maskedkunisquat.wulfpak.core.data.entity.Task
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Converts raw DB records into deterministic third-person fact statements
 * before they reach the LLM. Keeps the model's job to language only —
 * no perspective inference, no relationship reasoning.
 *
 * Rules:
 * - Activity/interaction bodies are EXCLUDED: they're first-person user
 *   notes ("did X, went to Y") that small models consistently misattribute
 *   to the contact. Only title, type, and date are safe structured facts.
 * - Notes tab content IS included as labeled quotes: the user writes these
 *   ABOUT the contact ("loves hiking, works at Google"), not about their
 *   own actions, so they're genuinely third-person observations.
 * - Life event notes are included as annotations for the same reason.
 */
internal object FactExtractor {

    private val dateFmt = SimpleDateFormat("MMM d, yyyy", Locale.ROOT)

    fun extract(
        person: Person,
        interactions: List<Interaction>,
        notes: List<Note>,
        activities: List<Activity>,
        lifeEvents: List<LifeEvent>,
        gifts: List<Gift>,
        tasks: List<Task>,
    ): String {
        val name = buildString {
            append(person.firstName)
            person.lastName?.let { append(" $it") }
        }

        val facts = mutableListOf<String>()

        // --- Identity ---
        facts += "$name is the user's ${person.relationLabel.replace('_', ' ')}."
        person.nickname?.let { facts += "$name goes by \"$it\"." }
        person.closenessRating?.let { facts += "Closeness rating: $it out of 5." }

        // --- Last contact ---
        val lastContactMs = person.lastContactedAt
        if (lastContactMs != null) {
            val days = ((System.currentTimeMillis() - lastContactMs) / 86_400_000L).toInt()
            facts += when (days) {
                0    -> "The user last had contact with $name today."
                1    -> "The user last had contact with $name yesterday."
                else -> "The user last had contact with $name $days days ago (${dateFmt.format(Date(lastContactMs))})."
            }
        } else {
            facts += "No contact has been logged yet."
        }

        // --- Life events ---
        lifeEvents.forEach { e ->
            val type    = e.eventType.replace('_', ' ')
            val dateStr = dateFmt.format(Date(e.date))
            val recur   = if (e.isRecurring) " (annual)" else ""
            val annotation = e.note?.let { " — \"$it\"" } ?: ""
            facts += "$name has a $type on $dateStr$recur$annotation."
        }

        // --- Interactions — type + date only, no free-text body ---
        if (interactions.isNotEmpty()) {
            val total = interactions.size
            facts += "The user has logged $total interaction${if (total == 1) "" else "s"} with $name."
            interactions.take(5).forEach { i ->
                val type    = i.type.replace('_', ' ')
                val dateStr = dateFmt.format(Date(i.timestamp))
                facts += "On $dateStr, the user had a $type with $name."
            }
        }

        // --- Activities — title + date only, no free-text body ---
        if (activities.isNotEmpty()) {
            val total = activities.size
            facts += "The user has shared $total activit${if (total == 1) "y" else "ies"} with $name."
            activities.take(5).forEach { a ->
                val dateStr = dateFmt.format(Date(a.timestamp))
                facts += "On $dateStr, the user and $name took part in: ${a.title}."
            }
        }

        // --- Notes — included as-is: written ABOUT the contact, not about user's actions ---
        if (notes.isNotEmpty()) {
            notes.take(10).forEach { n ->
                val dateStr = dateFmt.format(Date(n.timestamp))
                facts += "User observation about $name (${dateStr}): \"${n.body}\""
            }
        }

        // --- Open tasks ---
        val openTasks = tasks.filter { !it.isDone }
        if (openTasks.isNotEmpty()) {
            openTasks.forEach { t ->
                val due = t.dueAt?.let { " (due ${dateFmt.format(Date(it))})" } ?: ""
                facts += "Open task for $name: \"${t.title}\"$due."
            }
        }

        // --- Pending gifts ---
        val pendingGifts = gifts.filter { it.status != GiftStatus.GIVEN }
        if (pendingGifts.isNotEmpty()) {
            pendingGifts.forEach { g ->
                val occasion = g.occasion?.let { " for $it" } ?: ""
                facts += "Gift idea for $name: \"${g.name}\" (${g.status.lowercase()})$occasion."
            }
        }

        return buildString {
            appendLine("FACTS ABOUT $name:")
            facts.forEach { appendLine("- $it") }
        }
    }
}
