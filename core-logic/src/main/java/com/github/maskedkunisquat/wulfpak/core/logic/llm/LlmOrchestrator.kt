package com.github.maskedkunisquat.wulfpak.core.logic.llm

import com.github.maskedkunisquat.wulfpak.core.data.dao.InteractionDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.NoteDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.PersonDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class LlmOrchestrator(
    private val provider: LlmProvider,
    private val personDao: PersonDao,
    private val interactionDao: InteractionDao,
    private val noteDao: NoteDao,
) {
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)

    fun summarize(personId: UUID): Flow<LlmResult> = flow {
        val person = personDao.getById(personId) ?: run {
            emit(LlmResult.Error(IllegalArgumentException("Person $personId not found")))
            return@flow
        }
        val interactions = interactionDao.getForPerson(personId).first()
        val notes = noteDao.getForPerson(personId).first()

        val context = buildString {
            val name = buildString {
                append(person.firstName)
                person.lastName?.let { append(" $it") }
            }
            appendLine("Relationship with $name (${person.relationLabel}):")
            if (interactions.isNotEmpty()) {
                appendLine()
                appendLine("Recent interactions:")
                interactions.take(30).forEach { i ->
                    val date = dateFmt.format(Date(i.timestamp))
                    append("- [$date] ${i.type}")
                    i.note?.let { append(": $it") }
                    appendLine()
                }
            }
            if (notes.isNotEmpty()) {
                appendLine()
                appendLine("Notes:")
                notes.take(20).forEach { n ->
                    appendLine("- [${dateFmt.format(Date(n.timestamp))}] ${n.body}")
                }
            }
        }

        val prompt = "$context\nSummarize this person and anything important to remember about them."
        emitAll(provider.process(prompt))
    }

    fun query(naturalLanguage: String): Flow<LlmResult> =
        provider.process(naturalLanguage)

    fun suggestFollowUp(personId: UUID, daysSinceContact: Int): Flow<LlmResult> = flow {
        val person = personDao.getById(personId) ?: run {
            emit(LlmResult.Error(IllegalArgumentException("Person $personId not found")))
            return@flow
        }
        val prompt = "I haven't contacted ${person.firstName} (my ${person.relationLabel}) " +
            "in $daysSinceContact days. Suggest a brief, warm message to reconnect."
        emitAll(provider.process(prompt))
    }
}
