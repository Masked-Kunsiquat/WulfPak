package com.github.maskedkunisquat.wulfpak.core.logic.llm

import com.github.maskedkunisquat.wulfpak.core.data.calculateAge
import com.github.maskedkunisquat.wulfpak.core.data.entity.Activity
import com.github.maskedkunisquat.wulfpak.core.data.entity.Gift
import com.github.maskedkunisquat.wulfpak.core.data.entity.GiftStatus
import com.github.maskedkunisquat.wulfpak.core.data.entity.Interaction
import com.github.maskedkunisquat.wulfpak.core.data.entity.LifeEvent
import com.github.maskedkunisquat.wulfpak.core.data.entity.LifeEventType
import com.github.maskedkunisquat.wulfpak.core.data.entity.Note
import com.github.maskedkunisquat.wulfpak.core.data.entity.Person
import com.github.maskedkunisquat.wulfpak.core.data.entity.Task
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal object FactExtractor {

    private val shortFmt = SimpleDateFormat("MMM d", Locale.ENGLISH)

    // -------------------------------------------------------------------------
    // Programmatic summary — deterministic, zero hallucination.
    // Used by LlmOrchestrator.summarize() instead of routing through the LLM.
    // -------------------------------------------------------------------------

    fun buildSummary(
        person: Person,
        interactions: List<Interaction>,
        notes: List<Note>,
        activities: List<Activity>,
        lifeEvents: List<LifeEvent>,
        gifts: List<Gift>,
        tasks: List<Task>,
    ): String {
        val firstName = person.firstName
        val name = buildString {
            append(firstName)
            person.lastName?.let { append(" $it") }
        }
        val relation = person.relationLabel.replace('_', ' ')
        val sentences = mutableListOf<String>()

        // 1. Identity
        sentences += buildString {
            append("$name is your $relation")
            person.nickname?.let { append(", known as \"$it\"") }
            person.closenessRating?.let { append(", with a closeness rating of $it/5") }
            append(".")
        }

        // 2. Last contact
        val lastMs = person.lastContactedAt
        if (lastMs != null) {
            val days = ((System.currentTimeMillis() - lastMs) / 86_400_000L).toInt()
            val ago  = when (days) { 0 -> "today"; 1 -> "yesterday"; else -> "$days days ago" }
            val via  = interactions.firstOrNull()?.let { " via ${it.type.replace('_', ' ')}" } ?: ""
            sentences += "You last had contact $ago$via."
        } else {
            sentences += "No contact has been logged yet."
        }

        // 3. Shared history (activities take priority for the "most recently" callout)
        val actCount = activities.size
        val intCount = interactions.size
        if (actCount > 0 || intCount > 0) {
            sentences += buildString {
                when {
                    actCount > 0 && intCount > 0 -> {
                        val r = activities.first()
                        append("You've shared $actCount ${pl(actCount, "activity", "activities")} ")
                        append("and logged $intCount ${pl(intCount, "interaction")} with $firstName, ")
                        append("most recently \"${r.title}\" on ${shortFmt.format(Date(r.timestamp))}.")
                    }
                    actCount > 0 -> {
                        val r = activities.first()
                        append("You've shared $actCount ${pl(actCount, "activity", "activities")} with $firstName, ")
                        append("most recently \"${r.title}\" on ${shortFmt.format(Date(r.timestamp))}.")
                    }
                    else -> {
                        val r = interactions.first()
                        append("You've logged $intCount ${pl(intCount, "interaction")} with $firstName, ")
                        append("most recently a ${r.type.replace('_', ' ')} on ${shortFmt.format(Date(r.timestamp))}.")
                    }
                }
            }
        }

        // 4. Birthday callout (recurring) or generic life event count
        val birthday  = lifeEvents.firstOrNull { it.eventType == LifeEventType.BIRTHDAY && it.isRecurring }
        val deathDate = lifeEvents.firstOrNull { it.eventType == LifeEventType.DEATH }?.date
        if (birthday != null) {
            val daysUntil  = daysUntilNextOccurrence(birthday.date)
            val currentAge = if (birthYearIsKnown(birthday.date)) birthday.date.calculateAge() else null
            sentences += when {
                daysUntil == 0  -> "$firstName's birthday is today${currentAge?.let { " — turning $it" } ?: ""}!"
                daysUntil <= 30 -> "$firstName's birthday is coming up in $daysUntil days${currentAge?.let { " — turning ${it + 1}" } ?: ""}."
                else            -> "$firstName's birthday is ${shortFmt.format(Date(birthday.date))}${currentAge?.let { " — $it years old" } ?: ""}."
            }
        } else if (lifeEvents.isNotEmpty()) {
            sentences += "$firstName has ${lifeEvents.size} recorded life ${pl(lifeEvents.size, "event")}."
        }

        // 5. Pending items (tasks + gifts)
        val openTasks    = tasks.filter { !it.isDone }
        val pendingGifts = gifts.filter { it.status != GiftStatus.GIVEN }
        if (openTasks.isNotEmpty() || pendingGifts.isNotEmpty()) {
            val parts = buildList {
                if (openTasks.isNotEmpty())    add("${openTasks.size} open ${pl(openTasks.size, "task")}")
                if (pendingGifts.isNotEmpty()) add("${pendingGifts.size} pending gift ${pl(pendingGifts.size, "idea")}")
            }
            sentences += "You have ${parts.joinToString(" and ")} for $firstName."
        }

        return sentences.joinToString(" ")
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun pl(n: Int, singular: String, plural: String = "${singular}s") =
        if (n == 1) singular else plural

    private fun birthYearIsKnown(ms: Long): Boolean =
        Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.YEAR) != 1900

    private fun daysUntilNextOccurrence(birthdayMs: Long): Int {
        val bday = Calendar.getInstance().apply { timeInMillis = birthdayMs }
        val next = Calendar.getInstance().apply {
            set(Calendar.MONTH, bday.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, bday.get(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val now = Calendar.getInstance()
        if (!next.after(now)) next.add(Calendar.YEAR, 1)
        return ((next.timeInMillis - now.timeInMillis) / 86_400_000L).toInt()
    }
}
