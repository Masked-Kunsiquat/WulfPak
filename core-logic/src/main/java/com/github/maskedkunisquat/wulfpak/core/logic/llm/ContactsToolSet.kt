package com.github.maskedkunisquat.wulfpak.core.logic.llm

import android.util.Log
import com.github.maskedkunisquat.wulfpak.core.data.dao.ActivityDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.GiftDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.InteractionDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.LifeEventDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.NoteDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.PersonDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.PersonRelationshipDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.TaskDao
import com.github.maskedkunisquat.wulfpak.core.data.entity.GiftStatus
import com.github.maskedkunisquat.wulfpak.core.data.entity.Person
import com.github.maskedkunisquat.wulfpak.core.logic.search.SearchHit
import com.github.maskedkunisquat.wulfpak.core.logic.search.SearchRepository
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal class ContactsToolSet(
    private val personDao: PersonDao,
    private val interactionDao: InteractionDao,
    private val noteDao: NoteDao,
    private val activityDao: ActivityDao,
    private val lifeEventDao: LifeEventDao,
    private val giftDao: GiftDao,
    private val taskDao: TaskDao,
    private val searchRepository: SearchRepository,
    private val personRelationshipDao: PersonRelationshipDao,
) : ToolSet {

    private companion object {
        const val TAG = "ContactsToolSet"
        val fmt = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)
    }

    // Called by LlmOrchestrator to receive tool invocation events.
    @Volatile var eventSink: ((LlmResult.ToolCall) -> Unit)? = null

    // ── Tools ─────────────────────────────────────────────────────────────────

    @Tool(description = "Get notes for a contact by first name, or all recent notes across every contact if name is blank.")
    fun getContactNotes(
        @ToolParam(description = "First name or nickname. Leave blank to get the 15 most recent notes across all contacts.") name: String = "",
    ): String = runBlocking {
        Log.i(TAG, "getContactNotes — name=${name.ifBlank { "(all)" }}")
        eventSink?.invoke(LlmResult.ToolCall("getContactNotes", if (name.isBlank()) emptyMap() else mapOf("name" to name)))
        if (name.isBlank()) {
            val persons = personDao.getAllOnce()
            data class NoteEntry(val contactName: String, val timestamp: Long, val body: String)
            val allNotes = persons.flatMap { p ->
                val contactName = "${p.firstName}${p.lastName?.let { " $it" } ?: ""}"
                noteDao.getForPerson(p.id).first().map { n ->
                    NoteEntry(contactName, n.timestamp, n.body)
                }
            }.sortedByDescending { it.timestamp }.take(15)
            if (allNotes.isEmpty()) return@runBlocking "No notes found."
            allNotes.joinToString("\n") { "${it.contactName} (${fmt.format(Date(it.timestamp))}): \"${it.body}\"" }
        } else {
            val person = findPerson(name) ?: return@runBlocking "No contact found named \"$name\"."
            val notes = noteDao.getForPerson(person.id).first()
            if (notes.isEmpty()) return@runBlocking "No notes found for ${person.firstName}."
            notes.joinToString("\n") { n -> "- [${fmt.format(Date(n.timestamp))}] ${n.body}" }
        }
    }

    @Tool(description = "Get gift ideas and gifts for a contact by first name, or all pending gifts across every contact if name is blank.")
    fun getContactGifts(
        @ToolParam(description = "First name or nickname. Leave blank to get all pending gift ideas across all contacts.") name: String = "",
    ): String = runBlocking {
        Log.i(TAG, "getContactGifts — name=${name.ifBlank { "(all)" }}")
        eventSink?.invoke(LlmResult.ToolCall("getContactGifts", if (name.isBlank()) emptyMap() else mapOf("name" to name)))
        if (name.isBlank()) {
            val persons = personDao.getAllOnce()
            val lines = mutableListOf<String>()
            persons.forEach { p ->
                val gifts = giftDao.getForPerson(p.id).first().filter { it.status != GiftStatus.GIVEN }
                if (gifts.isNotEmpty()) {
                    val contactName = "${p.firstName}${p.lastName?.let { " $it" } ?: ""}"
                    gifts.forEach { g ->
                        val occasion = g.occasion?.let { " for $it" } ?: ""
                        val note = g.note?.let { " — $it" } ?: ""
                        lines += "$contactName: ${g.name} [${g.status.lowercase()}]$occasion$note"
                    }
                }
            }
            if (lines.isEmpty()) return@runBlocking "No pending gift ideas found."
            lines.joinToString("\n")
        } else {
            val person = findPerson(name) ?: return@runBlocking "No contact found named \"$name\"."
            val gifts = giftDao.getForPerson(person.id).first()
            if (gifts.isEmpty()) return@runBlocking "No gifts found for ${person.firstName}."
            gifts.joinToString("\n") { g ->
                val occasion = g.occasion?.let { " for $it" } ?: ""
                val note = g.note?.let { " — $it" } ?: ""
                "- ${g.name} [${g.status.lowercase()}]$occasion$note"
            }
        }
    }

    @Tool(description = "Get interaction and activity history for a contact by first name, or all recent history (last 30 days) across every contact if name is blank.")
    fun getContactHistory(
        @ToolParam(description = "First name or nickname. Leave blank to get interactions and activities from the last 30 days across all contacts.") name: String = "",
    ): String = runBlocking {
        Log.i(TAG, "getContactHistory — name=${name.ifBlank { "(all)" }}")
        eventSink?.invoke(LlmResult.ToolCall("getContactHistory", if (name.isBlank()) emptyMap() else mapOf("name" to name)))
        if (name.isBlank()) {
            val cutoff = System.currentTimeMillis() - 30L * 86_400_000L
            val persons = personDao.getAllOnce()
            data class Entry(val timestamp: Long, val line: String)
            val entries = mutableListOf<Entry>()
            persons.forEach { p ->
                val contactName = "${p.firstName}${p.lastName?.let { " $it" } ?: ""}"
                interactionDao.getForPerson(p.id).first()
                    .filter { it.timestamp >= cutoff }
                    .forEach { i ->
                        val note = i.note?.let { " — $it" } ?: ""
                        entries += Entry(i.timestamp, "${fmt.format(Date(i.timestamp))} — $contactName: ${i.type.replace('_', ' ')}$note")
                    }
                activityDao.getForPerson(p.id).first()
                    .filter { it.timestamp >= cutoff }
                    .forEach { a ->
                        entries += Entry(a.timestamp, "${fmt.format(Date(a.timestamp))} — $contactName: activity \"${a.title}\"")
                    }
            }
            if (entries.isEmpty()) return@runBlocking "No interactions or activities logged in the last 30 days."
            entries.sortedByDescending { it.timestamp }.joinToString("\n") { it.line }
        } else {
            val person = findPerson(name) ?: return@runBlocking "No contact found named \"$name\"."
            val interactions = interactionDao.getForPerson(person.id).first()
            val activities   = activityDao.getForPerson(person.id).first()
            if (interactions.isEmpty() && activities.isEmpty())
                return@runBlocking "No interaction history found for ${person.firstName}."
            buildString {
                if (interactions.isNotEmpty()) {
                    appendLine("Interactions with ${person.firstName}:")
                    interactions.forEach { i ->
                        val note = i.note?.let { " — $it" } ?: ""
                        appendLine("- ${fmt.format(Date(i.timestamp))}: ${i.type.replace('_', ' ')}$note")
                    }
                }
                if (activities.isNotEmpty()) {
                    appendLine("Activities with ${person.firstName}:")
                    activities.forEach { a ->
                        val body = a.body?.let { " — $it" } ?: ""
                        appendLine("- ${fmt.format(Date(a.timestamp))}: ${a.title}$body")
                    }
                }
            }.trimEnd()
        }
    }

    @Tool(description = "Get all open (not yet done) tasks. Optionally filter by contact first name; leave blank for all pending tasks.")
    fun getPendingTasks(
        @ToolParam(description = "Contact first name to filter by, or blank for all pending tasks.") name: String = "",
    ): String = runBlocking {
        Log.i(TAG, "getPendingTasks — name=${name.ifBlank { "(all)" }}")
        eventSink?.invoke(LlmResult.ToolCall("getPendingTasks", if (name.isBlank()) emptyMap() else mapOf("name" to name)))
        val tasks = if (name.isBlank()) {
            taskDao.getPending().first()
        } else {
            val person = findPerson(name) ?: return@runBlocking "No contact found named \"$name\"."
            taskDao.getForPerson(person.id).first().filter { !it.isDone }
        }
        if (tasks.isEmpty()) return@runBlocking "No pending tasks${if (name.isBlank()) "" else " for $name"}."
        tasks.joinToString("\n") { t ->
            val due = t.dueAt?.let { " (due ${fmt.format(Date(it))})" } ?: ""
            "- ${t.title}$due"
        }
    }

    @Tool(description = "Get a contact's profile — birthday, current age, relationship, job, and last contact date. Use this when asked about a contact's age, birthday, or general details.")
    fun getContactDetails(
        @ToolParam(description = "First name or nickname of the contact.") name: String,
    ): String = runBlocking {
        Log.i(TAG, "getContactDetails — name=$name")
        eventSink?.invoke(LlmResult.ToolCall("getContactDetails", mapOf("name" to name)))
        val person = findPerson(name) ?: return@runBlocking "No contact found named \"$name\"."
        val lifeEvents = lifeEventDao.getForPerson(person.id).first()
        val birthday   = lifeEvents.firstOrNull { it.eventType == "birthday" }
        val death      = lifeEvents.firstOrNull { it.eventType == "death" }
        buildString {
            val fullName = "${person.firstName}${person.lastName?.let { " $it" } ?: ""}"
            appendLine("$fullName — ${person.relationLabel.replace('_', ' ')}")
            person.nickname?.let { appendLine("Goes by: \"$it\"") }
            person.closenessRating?.let { appendLine("Closeness: $it/5") }
            listOfNotNull(person.jobTitle, person.company).joinToString(" at ").takeIf { it.isNotBlank() }
                ?.let { appendLine("Work: $it") }
            if (birthday != null) {
                val birthYear = Calendar.getInstance().apply { timeInMillis = birthday.date }.get(Calendar.YEAR)
                if (birthYear != 1900) {
                    val asOf = death?.date ?: System.currentTimeMillis()
                    val age  = calcAge(birthday.date, asOf)
                    appendLine("Birthday: ${fmt.format(Date(birthday.date))}")
                    if (death != null) appendLine("Age at passing: $age")
                    else appendLine("Current age: $age")
                } else {
                    appendLine("Birthday: ${SimpleDateFormat("MMM d", Locale.ENGLISH).format(Date(birthday.date))} (year unknown)")
                }
            }
            death?.let { appendLine("Passed away: ${fmt.format(Date(it.date))}") }
            person.lastContactedAt?.let { last ->
                val days = ((System.currentTimeMillis() - last) / 86_400_000L).toInt()
                val ago  = when (days) { 0 -> "today"; 1 -> "yesterday"; else -> "$days days ago" }
                appendLine("Last contact: $ago")
            } ?: appendLine("Last contact: never logged")
        }.trimEnd()
    }

    @Tool(description = "Search across all notes, interactions, and activities using natural language — topics, places, events, or phrases. Use this when asked to find a memory, conversation, or event by topic rather than by person.")
    fun searchAcrossContacts(
        @ToolParam(description = "The topic, place, event name, or phrase to search for.") query: String,
    ): String = runBlocking {
        Log.i(TAG, "searchAcrossContacts — query=$query")
        eventSink?.invoke(LlmResult.ToolCall("searchAcrossContacts", mapOf("query" to query)))
        val hits = try { searchRepository.search(query, limit = 8) } catch (_: Exception) { emptyList() }
        if (hits.isEmpty()) return@runBlocking "No results found for \"$query\"."
        val persons = personDao.getAllOnce()
        buildString {
            hits.forEach { hit ->
                when (hit) {
                    is SearchHit.NoteHit -> {
                        val who = hit.note.personId
                            ?.let { pid -> persons.firstOrNull { it.id == pid } }
                            ?.let { p -> "${p.firstName}${p.lastName?.let { " $it" } ?: ""}" }
                            ?: "standalone"
                        val body = if (hit.note.body.length > 200) hit.note.body.take(200) + "…" else hit.note.body
                        appendLine("Note for $who (${fmt.format(Date(hit.note.timestamp))}): \"$body\"")
                    }
                    is SearchHit.ActivityHit -> {
                        val body = hit.activity.body?.let { b ->
                            val s = if (b.length > 150) b.take(150) + "…" else b
                            ": $s"
                        } ?: ""
                        appendLine("Activity \"${hit.activity.title}\" on ${fmt.format(Date(hit.activity.timestamp))}$body")
                    }
                    is SearchHit.InteractionHit -> {
                        val note = hit.interaction.note?.let { n ->
                            val s = if (n.length > 120) n.take(120) + "…" else n
                            ": \"$s\""
                        } ?: ""
                        appendLine("${hit.interaction.type.replace('_', ' ')} on ${fmt.format(Date(hit.interaction.timestamp))}$note")
                    }
                }
            }
        }.trimEnd()
    }

    @Tool(description = "Get contacts with upcoming birthdays or anniversaries, sorted by soonest first.")
    fun getUpcomingEvents(): String = runBlocking {
        Log.i(TAG, "getUpcomingEvents called")
        eventSink?.invoke(LlmResult.ToolCall("getUpcomingEvents", emptyMap()))
        val events = lifeEventDao.getAllRecurring().first()
        if (events.isEmpty()) return@runBlocking "No recurring events found."
        val persons = personDao.getAllOnce()
        val now = Calendar.getInstance()
        events
            .mapNotNull { event ->
                val person = persons.firstOrNull { it.id == event.personId } ?: return@mapNotNull null
                val contactName = "${person.firstName}${person.lastName?.let { " $it" } ?: ""}"
                val bday = Calendar.getInstance().apply { timeInMillis = event.date }
                val next = Calendar.getInstance().apply {
                    set(Calendar.MONTH, bday.get(Calendar.MONTH))
                    set(Calendar.DAY_OF_MONTH, bday.get(Calendar.DAY_OF_MONTH))
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
                if (!next.after(now)) next.add(Calendar.YEAR, 1)
                val days = ((next.timeInMillis - now.timeInMillis) / 86_400_000L).toInt()
                val type = event.eventType.replace('_', ' ')
                Triple(days, contactName, "$contactName — $type on ${fmt.format(next.time)} (in $days days)")
            }
            .sortedBy { it.first }
            .take(10)
            .joinToString("\n") { it.third }
    }

    @Tool(description = "Get contacts you haven't been in touch with for a while, sorted by longest lapse first. Use this when asked who to call, who you've lost touch with, or who to reconnect with.")
    fun getLapsedContacts(
        @ToolParam(description = "Number of days since last contact to be considered lapsed. Default is 60.") days: Int = 60,
    ): String = runBlocking {
        Log.i(TAG, "getLapsedContacts — days=$days")
        eventSink?.invoke(LlmResult.ToolCall("getLapsedContacts", mapOf("days" to days.toString())))
        val cutoff = System.currentTimeMillis() - days * 86_400_000L
        val lapsed = personDao.getAllOnce()
            .filter { it.lastContactedAt == null || it.lastContactedAt < cutoff }
            .sortedBy { it.lastContactedAt ?: 0L }
        if (lapsed.isEmpty()) return@runBlocking "No contacts lapsed beyond $days days."
        lapsed.joinToString("\n") { p ->
            val name = "${p.firstName}${p.lastName?.let { " $it" } ?: ""}"
            val relation = p.relationLabel.replace("_", " ").lowercase()
            val lapse = when (val ts = p.lastContactedAt) {
                null -> "never contacted"
                else -> "${((System.currentTimeMillis() - ts) / 86_400_000L).toInt()} days ago"
            }
            "$name ($relation) — last contact: $lapse"
        }
    }

    @Tool(description = "Find contacts by their relationship type — e.g. 'friend', 'colleague', 'family', 'mentor'. Fuzzy match so 'friend' finds both Friend and Best Friend.")
    fun findContactsByRelation(
        @ToolParam(description = "Relationship type to search for, e.g. 'friend', 'colleague', 'family', 'mentor'.") relation: String,
    ): String = runBlocking {
        Log.i(TAG, "findContactsByRelation — relation=$relation")
        eventSink?.invoke(LlmResult.ToolCall("findContactsByRelation", mapOf("relation" to relation)))
        val query = relation.trim().lowercase()
        val matches = personDao.getAllOnce().filter { p ->
            p.relationLabel.lowercase().contains(query) ||
            p.relationLabel.replace("_", " ").lowercase().contains(query)
        }
        if (matches.isEmpty()) return@runBlocking "No contacts found with relation matching '$relation'."
        matches.joinToString("\n") { p ->
            val name = "${p.firstName}${p.lastName?.let { " $it" } ?: ""}"
            val relation = p.relationLabel.replace("_", " ").lowercase()
            val job = listOfNotNull(p.jobTitle, p.company).joinToString(" at ")
            val jobStr = if (job.isNotBlank()) " — $job" else ""
            "$name ($relation)$jobStr"
        }
    }

    @Tool(description = "Get all life events recorded for a contact — birthday, anniversaries, job changes, moves, graduations, deaths, etc.")
    fun getLifeEvents(
        @ToolParam(description = "First name or nickname of the contact.") name: String,
    ): String = runBlocking {
        Log.i(TAG, "getLifeEvents — name=$name")
        eventSink?.invoke(LlmResult.ToolCall("getLifeEvents", mapOf("name" to name)))
        val person = findPerson(name) ?: return@runBlocking "No contact found matching '$name'."
        val events = lifeEventDao.getForPersonOnce(person.id)
        if (events.isEmpty()) return@runBlocking "${person.firstName} has no recorded life events."
        events.joinToString("\n") { event ->
            val type = event.eventType.replace("_", " ").lowercase()
                .replaceFirstChar { it.uppercase() }
            val date = fmt.format(Date(event.date))
            val recurring = if (event.isRecurring) " (recurring)" else ""
            val note = event.note?.let { " — $it" } ?: ""
            "$type: $date$recurring$note"
        }
    }

    @Tool(description = "Get all person-to-person connections for a contact — who introduced them, family members, colleagues, partners, etc.")
    fun getRelationshipWeb(
        @ToolParam(description = "First name or nickname of the contact.") name: String,
    ): String = runBlocking {
        Log.i(TAG, "getRelationshipWeb — name=$name")
        eventSink?.invoke(LlmResult.ToolCall("getRelationshipWeb", mapOf("name" to name)))
        val person = findPerson(name) ?: return@runBlocking "No contact found matching '$name'."
        val connections = personRelationshipDao.getConnectionsForPersonOnce(person.id)
        if (connections.isEmpty()) return@runBlocking "${person.firstName} has no recorded connections."
        connections.joinToString("\n") { conn ->
            val other = "${conn.firstName}${conn.lastName?.let { " $it" } ?: ""}"
            "${person.firstName} → $other: ${conn.effectiveLabel}"
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun calcAge(birthdayMs: Long, asOfMs: Long): Int {
        val cal = Calendar.getInstance()
        cal.timeInMillis = asOfMs
        val nowYear = cal.get(Calendar.YEAR); val nowMonth = cal.get(Calendar.MONTH); val nowDay = cal.get(Calendar.DAY_OF_MONTH)
        cal.timeInMillis = birthdayMs
        var age = nowYear - cal.get(Calendar.YEAR)
        if (nowMonth < cal.get(Calendar.MONTH) || (nowMonth == cal.get(Calendar.MONTH) && nowDay < cal.get(Calendar.DAY_OF_MONTH))) age--
        return age
    }

    private suspend fun findPerson(name: String): Person? {
        val parts = name.trim().split(" ", limit = 2)
        return personDao.getAllOnce().firstOrNull { p ->
            p.firstName.equals(name, ignoreCase = true) ||
            p.nickname?.equals(name, ignoreCase = true) == true ||
            (parts.size == 2 &&
             p.firstName.equals(parts[0], ignoreCase = true) &&
             p.lastName?.equals(parts[1], ignoreCase = true) == true)
        }
    }
}
