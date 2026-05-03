package com.github.maskedkunisquat.wulfpak.core.logic.llm

import android.util.Log
import com.github.maskedkunisquat.wulfpak.core.logic.BuildConfig
import com.github.maskedkunisquat.wulfpak.core.data.calculateAge
import com.github.maskedkunisquat.wulfpak.core.data.dao.ActivityDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.GiftDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.InteractionDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.LifeEventDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.NoteDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.PersonDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.PersonRelationshipDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.TaskDao
import com.github.maskedkunisquat.wulfpak.core.data.entity.Gift
import com.github.maskedkunisquat.wulfpak.core.data.entity.GiftStatus
import com.github.maskedkunisquat.wulfpak.core.data.entity.Interaction
import com.github.maskedkunisquat.wulfpak.core.data.entity.InteractionParticipant
import com.github.maskedkunisquat.wulfpak.core.data.entity.InteractionType
import com.github.maskedkunisquat.wulfpak.core.data.entity.Note
import com.github.maskedkunisquat.wulfpak.core.data.entity.Person
import com.github.maskedkunisquat.wulfpak.core.data.entity.Task
import com.github.maskedkunisquat.wulfpak.core.data.entity.RelCategory
import com.github.maskedkunisquat.wulfpak.core.logic.family.FamilyInferenceEngine
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
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

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
    private val familyEngine: FamilyInferenceEngine,
) : ToolSet {

    private companion object {
        const val TAG = "ContactsToolSet"
        val fmt = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)
    }

    // Called by LlmOrchestrator to receive tool invocation events.
    @Volatile var eventSink: ((LlmResult.ToolCall) -> Unit)? = null

    // Called by LlmOrchestrator to receive pending-write events.
    @Volatile var writeSink: ((LlmResult.PendingWrite) -> Unit)? = null

    private val stagedWrites = ConcurrentHashMap<String, suspend () -> Unit>()

    suspend fun executePendingWrite(id: String) { stagedWrites.remove(id)?.invoke() }
    fun cancelPendingWrite(id: String) { stagedWrites.remove(id) }
    fun clearStagedWrites() { stagedWrites.clear() }

    // ── Tools ─────────────────────────────────────────────────────────────────

    @Tool(description = "Get notes for a contact by first name, or all recent notes across every contact if name is blank.")
    fun getContactNotes(
        @ToolParam(description = "First name or nickname. Leave blank to get the 15 most recent notes across all contacts.") name: String = "",
    ): String = runBlocking {
        if (BuildConfig.DEBUG) Log.d(TAG, "getContactNotes — name=${name.ifBlank { "(all)" }}")
        eventSink?.invoke(LlmResult.ToolCall("getContactNotes", if (name.isBlank()) emptyMap() else mapOf("name" to name)))
        if (name.isBlank()) {
            val persons = personDao.getAllOnce()
            val personById = persons.associateBy { it.id }
            data class NoteEntry(val contactName: String, val timestamp: Long, val body: String)
            val allNotes = noteDao.getAllOnce()
                .mapNotNull { n ->
                    val p = n.personId?.let { personById[it] } ?: return@mapNotNull null
                    NoteEntry("${p.firstName}${p.lastName?.let { " $it" } ?: ""}", n.timestamp, n.body)
                }
                .sortedByDescending { it.timestamp }.take(15)
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
        if (BuildConfig.DEBUG) Log.d(TAG, "getContactGifts — name=${name.ifBlank { "(all)" }}")
        eventSink?.invoke(LlmResult.ToolCall("getContactGifts", if (name.isBlank()) emptyMap() else mapOf("name" to name)))
        if (name.isBlank()) {
            val persons = personDao.getAllOnce()
            val personById = persons.associateBy { it.id }
            val lines = giftDao.getAllOnce()
                .filter { it.status != GiftStatus.GIVEN }
                .mapNotNull { g ->
                    val p = personById[g.personId] ?: return@mapNotNull null
                    val contactName = "${p.firstName}${p.lastName?.let { " $it" } ?: ""}"
                    val occasion = g.occasion?.let { " for $it" } ?: ""
                    val note = g.note?.let { " — $it" } ?: ""
                    "$contactName: ${g.name} [${g.status.lowercase()}]$occasion$note"
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

    @Tool(description = "Get recent history with a specific person — use for 'how has X been', 'recent events with X', 'what have I done with X'. Blank = last 30 days all contacts.")
    fun getContactHistory(
        @ToolParam(description = "First name or nickname. Leave blank to get interactions and activities from the last 30 days across all contacts.") name: String = "",
    ): String = runBlocking {
        if (BuildConfig.DEBUG) Log.d(TAG, "getContactHistory — name=${name.ifBlank { "(all)" }}")
        eventSink?.invoke(LlmResult.ToolCall("getContactHistory", if (name.isBlank()) emptyMap() else mapOf("name" to name)))
        if (name.isBlank()) {
            val cutoff = System.currentTimeMillis() - 30L * 86_400_000L
            val persons = personDao.getAllOnce()
            val personById = persons.associateBy { it.id }
            data class Entry(val timestamp: Long, val line: String)
            val entries = mutableListOf<Entry>()
            val recentInteractions = interactionDao.getAllOnce().filter { it.timestamp >= cutoff }
            val recentActivities   = activityDao.getAllOnce().filter { it.timestamp >= cutoff }
            val interactionParticipantsById = if (recentInteractions.isNotEmpty())
                interactionDao.getParticipantsForIds(recentInteractions.map { it.id })
                    .groupBy({ it.interactionId }, { it.personId })
            else emptyMap()
            val activityParticipantsById = if (recentActivities.isNotEmpty())
                activityDao.getParticipantsForIds(recentActivities.map { it.id })
                    .groupBy({ it.activityId }, { it.personId })
            else emptyMap()
            recentInteractions.forEach { i ->
                val participantIds = interactionParticipantsById[i.id] ?: emptyList()
                val note = i.note?.let { " — $it" } ?: ""
                participantIds.forEach { pid ->
                    val p = personById[pid] ?: return@forEach
                    val contactName = "${p.firstName}${p.lastName?.let { " $it" } ?: ""}"
                    val others = participantIds
                        .filter { it != pid }
                        .mapNotNull { personById[it] }
                        .map { op -> "${op.firstName}${op.lastName?.let { " $it" } ?: ""}" }
                    val withStr = if (others.isNotEmpty()) " [with: ${others.joinToString(", ")}]" else ""
                    entries += Entry(i.timestamp, "${fmt.format(Date(i.timestamp))} — $contactName: ${i.type.replace('_', ' ')}$note$withStr")
                }
            }
            recentActivities.forEach { a ->
                val participantIds = activityParticipantsById[a.id] ?: emptyList()
                participantIds.forEach { pid ->
                    val p = personById[pid] ?: return@forEach
                    val contactName = "${p.firstName}${p.lastName?.let { " $it" } ?: ""}"
                    val others = participantIds
                        .filter { it != pid }
                        .mapNotNull { personById[it] }
                        .map { op -> "${op.firstName}${op.lastName?.let { " $it" } ?: ""}" }
                    val withStr = if (others.isNotEmpty()) " [with: ${others.joinToString(", ")}]" else ""
                    entries += Entry(a.timestamp, "${fmt.format(Date(a.timestamp))} — $contactName: activity \"${a.title}\"$withStr")
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
            val persons = personDao.getAllOnce()
            val interactionParticipantsById = if (interactions.isNotEmpty())
                interactionDao.getParticipantsForIds(interactions.map { it.id })
                    .groupBy({ it.interactionId }, { it.personId })
            else emptyMap()
            val activityParticipantsById = if (activities.isNotEmpty())
                activityDao.getParticipantsForIds(activities.map { it.id })
                    .groupBy({ it.activityId }, { it.personId })
            else emptyMap()
            buildString {
                if (interactions.isNotEmpty()) {
                    appendLine("Interactions with ${person.firstName}:")
                    interactions.forEach { i ->
                        val note = i.note?.let { " — $it" } ?: ""
                        val others = (interactionParticipantsById[i.id] ?: emptyList())
                            .filter { it != person.id }
                            .mapNotNull { pid -> persons.firstOrNull { it.id == pid } }
                            .map { p -> "${p.firstName}${p.lastName?.let { " $it" } ?: ""}" }
                        val withStr = if (others.isNotEmpty()) " [with: ${others.joinToString(", ")}]" else ""
                        appendLine("- ${fmt.format(Date(i.timestamp))}: ${i.type.replace('_', ' ')}$note$withStr")
                    }
                }
                if (activities.isNotEmpty()) {
                    appendLine("Activities with ${person.firstName}:")
                    activities.forEach { a ->
                        val body = a.body?.let { " — $it" } ?: ""
                        val others = (activityParticipantsById[a.id] ?: emptyList())
                            .filter { it != person.id }
                            .mapNotNull { pid -> persons.firstOrNull { it.id == pid } }
                            .map { p -> "${p.firstName}${p.lastName?.let { " $it" } ?: ""}" }
                        val withStr = if (others.isNotEmpty()) " [with: ${others.joinToString(", ")}]" else ""
                        appendLine("- ${fmt.format(Date(a.timestamp))}: ${a.title}$body$withStr")
                    }
                }
            }.trimEnd()
        }
    }

    @Tool(description = "Get all open (not yet done) tasks. Optionally filter by contact first name; leave blank for all pending tasks.")
    fun getPendingTasks(
        @ToolParam(description = "Contact first name to filter by, or blank for all pending tasks.") name: String = "",
    ): String = runBlocking {
        if (BuildConfig.DEBUG) Log.d(TAG, "getPendingTasks — name=${name.ifBlank { "(all)" }}")
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

    @Tool(description = "Get the total number of contacts. Use this to verify or answer questions about how many contacts the user has.")
    fun getContactCount(): String = runBlocking {
        if (BuildConfig.DEBUG) Log.d(TAG, "getContactCount")
        eventSink?.invoke(LlmResult.ToolCall("getContactCount", emptyMap()))
        val count = personDao.getAllOnce().count { !it.isMe }
        "You have $count contacts."
    }

    @Tool(description = "Get a contact's profile — birthday, current age, relationship, job, and last contact date. Use this when asked about a contact's age, birthday, or general details.")
    fun getContactDetails(
        @ToolParam(description = "First name or nickname of the contact.") name: String,
    ): String = runBlocking {
        if (BuildConfig.DEBUG) Log.d(TAG, "getContactDetails — name=$name")
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
                    val age  = birthday.date.calculateAge(asOf)
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

    @Tool(description = "Keyword or topic search across notes, interactions, and activities — use for 'did I mention X', 'find Y conversation'. NOT for per-person history.")
    fun searchAcrossContacts(
        @ToolParam(description = "The topic, place, event name, or phrase to search for.") query: String,
    ): String = runBlocking {
        if (BuildConfig.DEBUG) Log.d(TAG, "searchAcrossContacts — query=$query")
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
                        val participantNames = activityDao.getParticipantIds(hit.activity.id)
                            .mapNotNull { pid -> persons.firstOrNull { it.id == pid } }
                            .map { p -> "${p.firstName}${p.lastName?.let { " $it" } ?: ""}" }
                        val withStr = if (participantNames.isNotEmpty()) " [with: ${participantNames.joinToString(", ")}]" else ""
                        appendLine("Activity \"${hit.activity.title}\" on ${fmt.format(Date(hit.activity.timestamp))}$body$withStr")
                    }
                    is SearchHit.InteractionHit -> {
                        val note = hit.interaction.note?.let { n ->
                            val s = if (n.length > 120) n.take(120) + "…" else n
                            ": \"$s\""
                        } ?: ""
                        val participantNames = interactionDao.getParticipantIds(hit.interaction.id)
                            .mapNotNull { pid -> persons.firstOrNull { it.id == pid } }
                            .map { p -> "${p.firstName}${p.lastName?.let { " $it" } ?: ""}" }
                        val withStr = if (participantNames.isNotEmpty()) " [with: ${participantNames.joinToString(", ")}]" else ""
                        appendLine("${hit.interaction.type.replace('_', ' ')} on ${fmt.format(Date(hit.interaction.timestamp))}$note$withStr")
                    }
                }
            }
        }.trimEnd()
    }

    @Tool(description = "Get contacts with upcoming birthdays or anniversaries, sorted by soonest first.")
    fun getUpcomingEvents(): String = runBlocking {
        if (BuildConfig.DEBUG) Log.d(TAG, "getUpcomingEvents called")
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
        if (BuildConfig.DEBUG) Log.d(TAG, "getLapsedContacts — days=$days")
        eventSink?.invoke(LlmResult.ToolCall("getLapsedContacts", mapOf("days" to days.toString())))
        val cutoff = System.currentTimeMillis() - days * 86_400_000L
        val lapsed = personDao.getAllOnce()
            .filter { !it.isMe }
            .filter { val t = it.lastContactedAt; t == null || t < cutoff }
            .sortedBy { it.lastContactedAt ?: 0L }
        if (lapsed.isEmpty()) return@runBlocking "No contacts lapsed beyond $days days."
        lapsed.joinToString("\n") { p ->
            val name = "${p.firstName}${p.lastName?.let { " $it" } ?: ""}"
            val relation = p.relationLabel.replace("_", " ").lowercase()
            val ts = p.lastContactedAt
            val lapse = if (ts == null) "never contacted"
                        else "${((System.currentTimeMillis() - ts) / 86_400_000L).toInt()} days ago"
            "$name ($relation) — last contact: $lapse"
        }
    }

    @Tool(description = "Find contacts by their relationship type — e.g. 'friend', 'colleague', 'family', 'mentor'. Fuzzy match so 'friend' finds both Friend and Best Friend.")
    fun findContactsByRelation(
        @ToolParam(description = "Relationship type to search for, e.g. 'friend', 'colleague', 'family', 'mentor'.") relation: String,
    ): String = runBlocking {
        if (BuildConfig.DEBUG) Log.d(TAG, "findContactsByRelation — relation=$relation")
        eventSink?.invoke(LlmResult.ToolCall("findContactsByRelation", mapOf("relation" to relation)))
        val query = relation.trim().lowercase()
        val matches = personDao.getAllOnce().filter { p ->
            !p.isMe && (p.relationLabel.lowercase().contains(query) ||
            p.relationLabel.replace("_", " ").lowercase().contains(query))
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
        if (BuildConfig.DEBUG) Log.d(TAG, "getLifeEvents — name=$name")
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
        if (BuildConfig.DEBUG) Log.d(TAG, "getRelationshipWeb — name=$name")
        eventSink?.invoke(LlmResult.ToolCall("getRelationshipWeb", mapOf("name" to name)))
        val person = findPerson(name) ?: return@runBlocking "No contact found matching '$name'."
        val connections = personRelationshipDao.getConnectionsForPersonOnce(person.id)
        if (connections.isEmpty()) return@runBlocking "${person.firstName} has no recorded connections."
        buildString {
            connections.forEach { conn ->
                val other = "${conn.firstName}${conn.lastName?.let { " $it" } ?: ""}"
                appendLine("${person.firstName} → $other: ${conn.effectiveLabel}")
            }
            val kin = familyEngine.inferKinOf(person.id)
            if (kin.isNotEmpty()) {
                appendLine("Inferred kin:")
                kin.forEach { k -> appendLine("- ${k.name}: ${k.kinLabel}") }
            }
        }.trimEnd()
    }

    @Tool(description = "List all family relationships inferred for a contact via graph traversal. Use for 'who are X's relatives', 'what family does X have', 'how is X related to Y'.")
    fun inferKinship(
        @ToolParam(description = "First name or nickname of the contact.") name: String,
    ): String = runBlocking {
        if (BuildConfig.DEBUG) Log.d(TAG, "inferKinship — name=$name")
        eventSink?.invoke(LlmResult.ToolCall("inferKinship", mapOf("name" to name)))
        val person = findPerson(name) ?: return@runBlocking "No contact found named \"$name\"."
        val kin = familyEngine.inferKinOf(person.id)
        if (kin.isEmpty()) return@runBlocking "No inferred kin found for ${person.firstName} (direct family edges may exist)."
        kin.joinToString("\n") { "${it.name}: ${it.kinLabel}" }
    }

    @Tool(description = "Infer how two contacts are related to each other by traversing family edges. Use for 'how are X and Y related', 'what is X to Y', 'are X and Y siblings'.")
    fun inferRelationBetween(
        @ToolParam(description = "First name or nickname of the first contact.") nameA: String,
        @ToolParam(description = "First name or nickname of the second contact.") nameB: String,
    ): String = runBlocking {
        if (BuildConfig.DEBUG) Log.d(TAG, "inferRelationBetween — nameA=$nameA nameB=$nameB")
        eventSink?.invoke(LlmResult.ToolCall("inferRelationBetween", mapOf("nameA" to nameA, "nameB" to nameB)))
        val personA = findPerson(nameA) ?: return@runBlocking "No contact found named \"$nameA\"."
        val personB = findPerson(nameB) ?: return@runBlocking "No contact found named \"$nameB\"."
        val label = familyEngine.inferBetween(personA.id, personB.id)
        if (label != null) {
            "${personA.firstName} → ${personB.firstName}: $label"
        } else {
            val reverse = familyEngine.inferBetween(personB.id, personA.id)
            if (reverse != null) "${personB.firstName} → ${personA.firstName}: $reverse"
            else "${personA.firstName} and ${personB.firstName} have no detectable family relationship."
        }
    }

    @Tool(description = "Explain why a contact's closeness score has drifted from their rating. Use for 'why am I drifting from X', 'how close am I to X', 'closeness insight for X'.")
    fun getClosenessInsight(
        @ToolParam(description = "First name or nickname of the contact.") name: String,
    ): String = runBlocking {
        if (BuildConfig.DEBUG) Log.d(TAG, "getClosenessInsight — name=$name")
        eventSink?.invoke(LlmResult.ToolCall("getClosenessInsight", mapOf("name" to name)))
        val person = findPerson(name) ?: return@runBlocking "No contact found named \"$name\"."
        val score = person.closenessScore
            ?: return@runBlocking "No closeness data yet for ${person.firstName}. Log some interactions to build a score."
        val rating = person.closenessRating
        val scoreStr = "%.2f".format(score)
        if (rating == null)
            return@runBlocking "${person.firstName}'s interaction score is $scoreStr (0–1 scale). No closeness rating has been set."
        val expectedFloor = (rating - 1) / 4f
        val driftThreshold = expectedFloor - 0.15f
        val ratingDesc = when (rating) {
            1 -> "acquaintance-level"; 2 -> "casual"; 3 -> "moderate"
            4 -> "close"; 5 -> "very close"; else -> "rated $rating/5"
        }
        val isDrifting = rating >= 4 && score < driftThreshold
        if (isDrifting) {
            "${person.firstName} is rated $rating/5 ($ratingDesc) but the interaction score is only $scoreStr" +
            " — below the drift threshold of ${"%.2f".format(driftThreshold)}." +
            " You're not connecting as often as intended. Consider reaching out."
        } else {
            val onTrack = score >= expectedFloor
            val healthStr = if (onTrack) "on track" else "slightly below target but not yet drifting"
            "${person.firstName} is rated $rating/5 ($ratingDesc) and the interaction score is $scoreStr. Relationship is $healthStr."
        }
    }

    // ── Write tools ───────────────────────────────────────────────────────────

    @Tool(description = "Log an interaction (call, text, email, video call, in-person meeting, or social media) with a contact.")
    fun logInteraction(
        @ToolParam(description = "First name or nickname of the contact.") name: String,
        @ToolParam(description = "Interaction type: call, text, email, video_call, in_person, or social_media.") type: String,
        @ToolParam(description = "Optional short note about the interaction. Leave blank if none.") note: String = "",
    ): String = runBlocking {
        if (BuildConfig.DEBUG) Log.d(TAG, "logInteraction — name=$name type=$type")
        val person = findPerson(name) ?: return@runBlocking "No contact found named \"$name\". Ask the user to clarify."
        val normalizedType = normalizeInteractionType(type)
        val writeId = UUID.randomUUID().toString()
        val description = "Log ${normalizedType.replace('_', ' ')} with ${person.firstName}"
        val ts = System.currentTimeMillis()
        stagedWrites[writeId] = {
            val interaction = Interaction(timestamp = ts, type = normalizedType, note = note.ifBlank { null })
            interactionDao.insert(interaction)
            interactionDao.insertParticipant(InteractionParticipant(interaction.id, person.id))
            personDao.onInteractionAdded(person.id, ts)
        }
        eventSink?.invoke(LlmResult.ToolCall("logInteraction", mapOf("name" to name, "type" to normalizedType)))
        writeSink?.invoke(LlmResult.PendingWrite(writeId, description))
        "Queued. Tell the user: \"$description\" is ready to confirm using the button that will appear."
    }

    @Tool(description = "Add a note about a contact.")
    fun addNote(
        @ToolParam(description = "First name or nickname of the contact.") name: String,
        @ToolParam(description = "The note text.") body: String,
    ): String = runBlocking {
        if (BuildConfig.DEBUG) Log.d(TAG, "addNote — name=$name")
        val person = findPerson(name) ?: return@runBlocking "No contact found named \"$name\". Ask the user to clarify."
        val writeId = UUID.randomUUID().toString()
        val description = "Add note to ${person.firstName}"
        val ts = System.currentTimeMillis()
        stagedWrites[writeId] = {
            noteDao.insert(Note(personId = person.id, timestamp = ts, body = body))
        }
        eventSink?.invoke(LlmResult.ToolCall("addNote", mapOf("name" to name, "body" to body)))
        writeSink?.invoke(LlmResult.PendingWrite(writeId, description))
        "Queued. Tell the user: \"$description\" is ready to confirm using the button that will appear."
    }

    @Tool(description = "Add a gift idea for a contact.")
    fun addGiftIdea(
        @ToolParam(description = "First name or nickname of the contact.") name: String,
        @ToolParam(description = "Name or description of the gift idea.") giftName: String,
        @ToolParam(description = "Occasion this gift is for (e.g. birthday, Christmas). Leave blank if none.") occasion: String = "",
    ): String = runBlocking {
        if (BuildConfig.DEBUG) Log.d(TAG, "addGiftIdea — name=$name giftName=$giftName")
        val person = findPerson(name) ?: return@runBlocking "No contact found named \"$name\". Ask the user to clarify."
        val writeId = UUID.randomUUID().toString()
        val description = "Add gift idea for ${person.firstName}: $giftName"
        stagedWrites[writeId] = {
            giftDao.insert(Gift(personId = person.id, name = giftName, occasion = occasion.ifBlank { null }))
        }
        eventSink?.invoke(LlmResult.ToolCall("addGiftIdea", mapOf("name" to name, "gift" to giftName)))
        writeSink?.invoke(LlmResult.PendingWrite(writeId, description))
        "Queued. Tell the user: \"$description\" is ready to confirm using the button that will appear."
    }

    @Tool(description = "Add a task related to a contact.")
    fun addTask(
        @ToolParam(description = "First name or nickname of the contact.") name: String,
        @ToolParam(description = "Task title.") title: String,
        @ToolParam(description = "Due in how many days. Leave blank for no due date.") dueInDays: String = "",
    ): String = runBlocking {
        if (BuildConfig.DEBUG) Log.d(TAG, "addTask — name=$name title=$title dueInDays=$dueInDays")
        val person = findPerson(name) ?: return@runBlocking "No contact found named \"$name\". Ask the user to clarify."
        val writeId = UUID.randomUUID().toString()
        val description = "Add task for ${person.firstName}: $title"
        val dueAt = dueInDays.trim().toIntOrNull()?.let { System.currentTimeMillis() + it * 86_400_000L }
        stagedWrites[writeId] = {
            taskDao.insert(Task(personId = person.id, title = title, dueAt = dueAt))
        }
        eventSink?.invoke(LlmResult.ToolCall("addTask", mapOf("name" to name, "title" to title)))
        writeSink?.invoke(LlmResult.PendingWrite(writeId, description))
        "Queued. Tell the user: \"$description\" is ready to confirm using the button that will appear."
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun normalizeInteractionType(raw: String): String {
        val s = raw.lowercase()
        return when {
            s.contains("call") || s.contains("phone") -> InteractionType.CALL
            s.contains("text") || s.contains("sms") || s.contains("message") -> InteractionType.TEXT
            s.contains("email") -> InteractionType.EMAIL
            s.contains("video") || s.contains("zoom") || s.contains("facetime") -> InteractionType.VIDEO_CALL
            s.contains("social") || s.contains("instagram") || s.contains("twitter") -> InteractionType.SOCIAL_MEDIA
            else -> InteractionType.IN_PERSON
        }
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
