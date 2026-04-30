package com.github.maskedkunisquat.wulfpak.core.logic.llm

import com.github.maskedkunisquat.wulfpak.core.data.dao.ActivityDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.GiftDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.InteractionDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.LifeEventDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.NoteDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.PersonDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.TaskDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
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
) {
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
        val persons = personDao.getAllOnce()
        val roster = buildString {
            if (persons.isEmpty()) {
                appendLine("CONTACTS: (none)")
            } else {
                appendLine("CONTACTS:")
                persons.forEach { p ->
                    val name = buildString {
                        append(p.firstName)
                        p.lastName?.let { append(" $it") }
                    }
                    append("- $name, ${p.relationLabel.replace('_', ' ')}")
                    p.nickname?.let { append(", known as \"$it\"") }
                    p.lastContactedAt?.let {
                        val days = ((System.currentTimeMillis() - it) / 86_400_000L).toInt()
                        append(", last contact $days days ago")
                    } ?: append(", no contact logged")
                    appendLine()
                }
            }
            appendLine()
            appendLine("QUESTION: $naturalLanguage")
        }
        emitAll(provider.process(roster, Prompts.QUERY_SYSTEM))
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
