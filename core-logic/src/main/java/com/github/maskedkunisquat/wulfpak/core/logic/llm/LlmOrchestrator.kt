package com.github.maskedkunisquat.wulfpak.core.logic.llm

import com.github.maskedkunisquat.wulfpak.core.data.dao.ActivityDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.GiftDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.InteractionDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.LifeEventDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.NoteDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.PersonDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.PersonRelationshipDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.TaskDao
import com.github.maskedkunisquat.wulfpak.core.logic.family.FamilyInferenceEngine
import com.github.maskedkunisquat.wulfpak.core.logic.search.SearchHit
import com.github.maskedkunisquat.wulfpak.core.logic.search.SearchRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

class LlmOrchestrator(
    private val provider: LlmProvider,
    private val personDao: PersonDao,
    private val interactionDao: InteractionDao,
    private val noteDao: NoteDao,
    private val activityDao: ActivityDao,
    private val lifeEventDao: LifeEventDao,
    private val giftDao: GiftDao,
    private val taskDao: TaskDao,
    private val searchRepository: SearchRepository,
    private val personRelationshipDao: PersonRelationshipDao,
    private val familyInferenceEngine: FamilyInferenceEngine,
) {
    private val contactsToolSet = ContactsToolSet(
        personDao, interactionDao, noteDao, activityDao, lifeEventDao, giftDao, taskDao,
        searchRepository, personRelationshipDao, familyInferenceEngine,
    )
    fun summarize(personId: UUID): Flow<LlmResult> = flow {
        val person = personDao.getById(personId) ?: run {
            emit(LlmResult.Error(IllegalArgumentException("Person $personId not found")))
            return@flow
        }
        val summary = FactExtractor.buildSummary(
            person       = person,
            interactions = interactionDao.getForPerson(personId).first(),
            notes        = noteDao.getForPerson(personId).first(),
            activities   = activityDao.getForPerson(personId).first(),
            lifeEvents   = lifeEventDao.getForPerson(personId).first(),
            gifts        = giftDao.getForPerson(personId).first(),
            tasks        = taskDao.getForPerson(personId).first(),
        )
        emit(LlmResult.Token(summary))
        emit(LlmResult.Complete)
    }

    fun query(naturalLanguage: String): Flow<LlmResult> = flow {
        val dateFmt = SimpleDateFormat("MMM d", Locale.ENGLISH)
        val persons = personDao.getAllOnce()
        val me = personDao.getMe()
        val hits = try { searchRepository.search(naturalLanguage, limit = 5) } catch (_: Exception) { emptyList() }

        val meProfile = me?.let { p ->
            buildString {
                appendLine("YOUR PROFILE:")
                val name = buildString {
                    append(p.firstName)
                    p.lastName?.let { append(" $it") }
                }
                append("- Name: $name")
                p.nickname?.let { append(" (\"$it\")") }
                appendLine()
                val job = listOfNotNull(p.jobTitle, p.company).joinToString(" at ")
                if (job.isNotBlank()) appendLine("- Works as $job")
            }
        }

        val contacts = persons.filter { !it.isMe }
        val roster = buildString {
            if (contacts.isEmpty()) {
                appendLine("CONTACTS: (none)")
            } else {
                val cap = 150
                val shown = contacts.take(cap)
                appendLine("CONTACTS: (${contacts.size} total)")
                shown.forEach { p ->
                    val name = buildString {
                        append(p.firstName)
                        p.lastName?.let { append(" $it") }
                    }
                    append("- $name")
                    p.nickname?.let { append(" (\"$it\")") }
                    appendLine()
                }
                if (contacts.size > cap) {
                    appendLine("(${contacts.size - cap} more — use findContactsByRelation or getLapsedContacts to browse)")
                }
            }
        }

        val userMsg = buildString {
            if (hits.isNotEmpty()) {
                appendLine("RELEVANT RECORDS:")
                hits.forEach { hit ->
                    when (hit) {
                        is SearchHit.NoteHit -> {
                            val date = dateFmt.format(hit.note.timestamp)
                            val body = hit.note.body.let { if (it.length > 120) it.take(120) + "…" else it }
                            appendLine("- Note ($date): \"$body\"")
                        }
                        is SearchHit.ActivityHit -> {
                            val date = dateFmt.format(hit.activity.timestamp)
                            appendLine("- Activity \"${hit.activity.title}\" on $date")
                        }
                        is SearchHit.InteractionHit -> {
                            val date = dateFmt.format(hit.interaction.timestamp)
                            val type = hit.interaction.type.replace('_', ' ')
                            appendLine("- Interaction: $type on $date")
                        }
                    }
                }
                appendLine()
            }
            append("QUESTION: $naturalLanguage")
        }

        // Buffer tool/write events (emitted from LiteRT's thread inside @Tool methods).
        // Flushed just before the first token so they appear inline in the chat above the response.
        val pendingBuffer = ArrayList<LlmResult>()
        contactsToolSet.eventSink = { pendingBuffer.add(it) }
        contactsToolSet.writeSink  = { pendingBuffer.add(it) }
        try {
            val systemPrompt = buildString {
                append(Prompts.QUERY_SYSTEM)
                if (meProfile != null) { appendLine(); appendLine(); append(meProfile.trimEnd()) }
                appendLine(); appendLine(); append(roster.trimEnd())
            }
            provider.chatSend(userMsg, systemPrompt, listOf(contactsToolSet))
                .collect { result ->
                    if (pendingBuffer.isNotEmpty()) {
                        pendingBuffer.forEach { emit(it) }
                        pendingBuffer.clear()
                    }
                    emit(result)
                }
        } finally {
            contactsToolSet.eventSink = null
            contactsToolSet.writeSink  = null
        }
    }

    suspend fun executePendingWrite(id: String) = contactsToolSet.executePendingWrite(id)
    fun cancelPendingWrite(id: String) = contactsToolSet.cancelPendingWrite(id)

    fun resetChat() {
        contactsToolSet.clearStagedWrites()
        provider.resetChat()
    }

    fun suggestFollowUp(personId: UUID, daysSinceContact: Int): Flow<LlmResult> = flow {
        val person = personDao.getById(personId) ?: run {
            emit(LlmResult.Error(IllegalArgumentException("Person $personId not found")))
            return@flow
        }
        val prompt = "I haven't contacted ${person.firstName} (my ${person.relationLabel.replace('_', ' ')}) " +
            "in $daysSinceContact days. Suggest a brief, warm message to reconnect."
        emitAll(provider.process(prompt, Prompts.FOLLOW_UP_SYSTEM))
    }
}
