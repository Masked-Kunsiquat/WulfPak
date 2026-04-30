package com.github.maskedkunisquat.wulfpak.core.logic.llm

import com.github.maskedkunisquat.wulfpak.core.data.dao.ActivityDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.GiftDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.InteractionDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.LifeEventDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.NoteDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.PersonDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.TaskDao
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
) {
    private val contactsToolSet = ContactsToolSet(
        personDao, interactionDao, noteDao, activityDao, lifeEventDao, giftDao, taskDao,
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
        val hits = try { searchRepository.search(naturalLanguage, limit = 5) } catch (_: Exception) { emptyList() }

        val roster = buildString {
            if (persons.isEmpty()) {
                appendLine("CONTACTS: (none)")
            } else {
                appendLine("CONTACTS: (${persons.size} total)")
                persons.forEach { p ->
                    val name = buildString {
                        append(p.firstName)
                        p.lastName?.let { append(" $it") }
                    }
                    append("- $name, ${p.relationLabel.replace('_', ' ')}")
                    p.nickname?.let { append(", known as \"$it\"") }
                    val job = listOfNotNull(p.jobTitle, p.company).joinToString(" at ")
                    if (job.isNotBlank()) append(", works as $job")
                    p.closenessRating?.let { append(", closeness $it/5") }
                    if (p.isFavorite) append(" [starred]")
                    p.lastContactedAt?.let {
                        val days = ((System.currentTimeMillis() - it) / 86_400_000L).toInt()
                        append(", last contact $days days ago")
                    } ?: append(", no contact logged")
                    appendLine()
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

        // Buffer tool call events (emitted from LiteRT's thread inside @Tool methods).
        // Flushed just before the first token so they appear inline in the chat above the response.
        val toolBuffer = ArrayList<LlmResult.ToolCall>()
        contactsToolSet.eventSink = { toolBuffer.add(it) }
        try {
            provider.chatSend(userMsg, Prompts.QUERY_SYSTEM + "\n\n" + roster, listOf(contactsToolSet))
                .collect { result ->
                    if (toolBuffer.isNotEmpty()) {
                        toolBuffer.forEach { emit(it) }
                        toolBuffer.clear()
                    }
                    emit(result)
                }
        } finally {
            contactsToolSet.eventSink = null
        }
    }

    fun resetChat() { provider.resetChat() }

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
