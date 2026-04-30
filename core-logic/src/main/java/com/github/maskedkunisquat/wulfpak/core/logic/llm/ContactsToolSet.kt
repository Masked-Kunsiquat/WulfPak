package com.github.maskedkunisquat.wulfpak.core.logic.llm

import android.util.Log
import com.github.maskedkunisquat.wulfpak.core.data.dao.ActivityDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.GiftDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.InteractionDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.LifeEventDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.NoteDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.PersonDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.TaskDao
import com.github.maskedkunisquat.wulfpak.core.data.entity.GiftStatus
import com.github.maskedkunisquat.wulfpak.core.data.entity.Person
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

    // ── Helpers ───────────────────────────────────────────────────────────────

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
