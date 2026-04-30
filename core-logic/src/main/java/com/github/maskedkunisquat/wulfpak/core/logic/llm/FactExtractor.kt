package com.github.maskedkunisquat.wulfpak.core.logic.llm

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

    private val longFmt  = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)
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
            val currentAge = if (birthYearIsKnown(birthday.date)) calculateAge(birthday.date) else null
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
    // Structured fact list — reserved for future LLM tool-calling / chat use.
    // When the user asks "tell me more about that Cancer Walk event," the LLM
    // can request specific records and receive a clean fact block like this.
    // -------------------------------------------------------------------------

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

        facts += "$name is the user's ${person.relationLabel.replace('_', ' ')}."
        person.nickname?.let { facts += "$name goes by \"$it\"." }
        person.closenessRating?.let { facts += "Closeness rating: $it out of 5." }

        val lastContactMs = person.lastContactedAt
        if (lastContactMs != null) {
            val days = ((System.currentTimeMillis() - lastContactMs) / 86_400_000L).toInt()
            facts += when (days) {
                0    -> "The user last had contact with $name today."
                1    -> "The user last had contact with $name yesterday."
                else -> "The user last had contact with $name $days days ago (${longFmt.format(Date(lastContactMs))})."
            }
        } else {
            facts += "No contact has been logged yet."
        }

        val birthdayEvent = lifeEvents.firstOrNull { it.eventType == LifeEventType.BIRTHDAY }
        val deathEvent    = lifeEvents.firstOrNull { it.eventType == LifeEventType.DEATH }

        lifeEvents.forEach { e ->
            val type       = e.eventType.replace('_', ' ')
            val dateStr    = longFmt.format(Date(e.date))
            val recur      = if (e.isRecurring) " (annual)" else ""
            val annotation = e.note?.let { " — \"$it\"" } ?: ""
            facts += "$name has a $type on $dateStr$recur$annotation."
        }

        // Explicit age fact so the LLM doesn't have to do arithmetic
        // Skip if birth year is the 1900 sentinel (year-less import from contacts)
        if (birthdayEvent != null && birthYearIsKnown(birthdayEvent.date)) {
            val asOf = deathEvent?.date ?: System.currentTimeMillis()
            val age  = calculateAge(birthdayEvent.date, asOf)
            if (deathEvent != null) {
                facts += "$name was $age years old when they passed away."
            } else {
                facts += "$name is currently $age years old."
            }
        }

        if (interactions.isNotEmpty()) {
            facts += "The user has logged ${interactions.size} ${pl(interactions.size, "interaction")} with $name."
            interactions.take(5).forEach { i ->
                facts += "On ${longFmt.format(Date(i.timestamp))}, the user had a ${i.type.replace('_', ' ')} with $name."
            }
        }

        if (activities.isNotEmpty()) {
            facts += "The user has shared ${activities.size} ${pl(activities.size, "activity", "activities")} with $name."
            activities.take(5).forEach { a ->
                facts += "On ${longFmt.format(Date(a.timestamp))}, the user and $name took part in: ${a.title}."
            }
        }

        if (notes.isNotEmpty()) {
            notes.take(10).forEach { n ->
                facts += "User observation about $name (${longFmt.format(Date(n.timestamp))}): \"${n.body}\""
            }
        }

        tasks.filter { !it.isDone }.forEach { t ->
            val due = t.dueAt?.let { " (due ${longFmt.format(Date(it))})" } ?: ""
            facts += "Open task for $name: \"${t.title}\"$due."
        }

        gifts.filter { it.status != GiftStatus.GIVEN }.forEach { g ->
            val occasion = g.occasion?.let { " for $it" } ?: ""
            facts += "Gift idea for $name: \"${g.name}\" (${g.status.lowercase()})$occasion."
        }

        return buildString {
            appendLine("FACTS ABOUT $name:")
            facts.forEach { appendLine("- $it") }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun pl(n: Int, singular: String, plural: String = "${singular}s") =
        if (n == 1) singular else plural

    private fun birthYearIsKnown(ms: Long): Boolean =
        Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.YEAR) != 1900

    private fun calculateAge(birthdayMs: Long, asOfMs: Long = System.currentTimeMillis()): Int {
        val cal = Calendar.getInstance()
        cal.timeInMillis = asOfMs
        val nowYear  = cal.get(Calendar.YEAR)
        val nowMonth = cal.get(Calendar.MONTH)
        val nowDay   = cal.get(Calendar.DAY_OF_MONTH)
        cal.timeInMillis = birthdayMs
        val birthYear  = cal.get(Calendar.YEAR)
        val birthMonth = cal.get(Calendar.MONTH)
        val birthDay   = cal.get(Calendar.DAY_OF_MONTH)
        var age = nowYear - birthYear
        if (nowMonth < birthMonth || (nowMonth == birthMonth && nowDay < birthDay)) age--
        return age
    }

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
