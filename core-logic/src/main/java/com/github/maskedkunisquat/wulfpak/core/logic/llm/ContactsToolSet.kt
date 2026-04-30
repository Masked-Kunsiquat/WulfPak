package com.github.maskedkunisquat.wulfpak.core.logic.llm

import com.github.maskedkunisquat.wulfpak.core.data.dao.ActivityDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.GiftDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.InteractionDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.LifeEventDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.NoteDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.PersonDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.TaskDao
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

    @Tool(description = "Get full details about a specific contact: their interactions, notes, activities, tasks, gifts, and life events.")
    fun getContactDetails(
        @ToolParam(description = "First name or nickname of the contact to look up") name: String,
    ): String = runBlocking {
        val person = personDao.getAllOnce().firstOrNull {
            it.firstName.equals(name, ignoreCase = true) ||
            it.nickname?.equals(name, ignoreCase = true) == true
        } ?: return@runBlocking "No contact found with name \"$name\"."
        FactExtractor.extract(
            person       = person,
            interactions = interactionDao.getForPerson(person.id).first(),
            notes        = noteDao.getForPerson(person.id).first(),
            activities   = activityDao.getForPerson(person.id).first(),
            lifeEvents   = lifeEventDao.getForPerson(person.id).first(),
            gifts        = giftDao.getForPerson(person.id).first(),
            tasks        = taskDao.getForPerson(person.id).first(),
        )
    }

    @Tool(description = "Get all open (not yet done) tasks. Optionally filter by contact name. Leave name blank for all pending tasks.")
    fun getPendingTasks(
        @ToolParam(description = "Contact first name to filter by. Default is empty (returns all pending tasks).") name: String = "",
    ): String = runBlocking {
        val tasks = if (name.isBlank()) {
            taskDao.getPending().first()
        } else {
            val person = personDao.getAllOnce().firstOrNull {
                it.firstName.equals(name, ignoreCase = true) ||
                it.nickname?.equals(name, ignoreCase = true) == true
            } ?: return@runBlocking "No contact found with name \"$name\"."
            taskDao.getForPerson(person.id).first().filter { !it.isDone }
        }
        if (tasks.isEmpty()) return@runBlocking "No pending tasks${if (name.isBlank()) "" else " for $name"}."
        val fmt = SimpleDateFormat("MMM d", Locale.ENGLISH)
        tasks.joinToString("\n") { t ->
            val due = t.dueAt?.let { " (due ${fmt.format(Date(it))})" } ?: ""
            "- ${t.title}$due"
        }
    }

    @Tool(description = "Get contacts with upcoming recurring events like birthdays or anniversaries, sorted by soonest first.")
    fun getUpcomingEvents(): String = runBlocking {
        val events = lifeEventDao.getAllRecurring().first()
        if (events.isEmpty()) return@runBlocking "No recurring events found."
        val persons = personDao.getAllOnce()
        val fmt = SimpleDateFormat("MMM d", Locale.ENGLISH)
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
}
