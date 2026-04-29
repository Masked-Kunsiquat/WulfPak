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

        val facts = FactExtractor.extract(
            person       = person,
            interactions = interactionDao.getForPerson(personId).first(),
            notes        = noteDao.getForPerson(personId).first(),
            activities   = activityDao.getForPerson(personId).first(),
            lifeEvents   = lifeEventDao.getForPerson(personId).first(),
            gifts        = giftDao.getForPerson(personId).first(),
            tasks        = taskDao.getForPerson(personId).first(),
        )

        emitAll(provider.process(facts, Prompts.SUMMARIZE_SYSTEM))
    }

    fun query(naturalLanguage: String): Flow<LlmResult> =
        provider.process(naturalLanguage)

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
