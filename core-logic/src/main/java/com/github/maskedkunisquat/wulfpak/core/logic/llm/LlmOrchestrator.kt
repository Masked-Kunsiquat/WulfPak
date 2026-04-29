package com.github.maskedkunisquat.wulfpak.core.logic.llm

import com.github.maskedkunisquat.wulfpak.core.data.dao.ActivityDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.GiftDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.InteractionDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.LifeEventDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.NoteDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.PersonDao
import com.github.maskedkunisquat.wulfpak.core.data.dao.TaskDao
import com.github.maskedkunisquat.wulfpak.core.data.entity.GiftStatus
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
    private val activityDao: ActivityDao,
    private val lifeEventDao: LifeEventDao,
    private val giftDao: GiftDao,
    private val taskDao: TaskDao,
) {
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)

    fun summarize(personId: UUID): Flow<LlmResult> = flow {
        val person = personDao.getById(personId) ?: run {
            emit(LlmResult.Error(IllegalArgumentException("Person $personId not found")))
            return@flow
        }

        val interactions = interactionDao.getForPerson(personId).first()
        val notes        = noteDao.getForPerson(personId).first()
        val activities   = activityDao.getForPerson(personId).first()
        val lifeEvents   = lifeEventDao.getForPerson(personId).first()
        val gifts        = giftDao.getForPerson(personId).first()
        val tasks        = taskDao.getForPerson(personId).first()

        val name = buildString {
            append(person.firstName)
            person.lastName?.let { append(" $it") }
        }

        val context = buildString {
            // Header
            append("Person: $name")
            append(", relation: ${person.relationLabel.replace('_', ' ')}")
            person.nickname?.let { append(", known as \"$it\"") }
            person.closenessRating?.let { append(", closeness ${it}/5") }
            person.lastContactedAt?.let {
                val days = ((System.currentTimeMillis() - it) / 86_400_000L).toInt()
                append(", last contact $days day${if (days == 1) "" else "s"} ago")
            }
            appendLine()

            // Life events (all — usually a short list)
            if (lifeEvents.isNotEmpty()) {
                appendLine()
                appendLine("Life events:")
                lifeEvents.forEach { e ->
                    val recurring = if (e.isRecurring) " (recurring)" else ""
                    append("- ${e.eventType.replace('_', ' ')}: ${dateFmt.format(Date(e.date))}$recurring")
                    e.note?.let { append(" — $it") }
                    appendLine()
                }
            }

            // Recent interactions
            if (interactions.isNotEmpty()) {
                appendLine()
                appendLine("Recent interactions (latest first):")
                interactions.take(20).forEach { i ->
                    append("- [${dateFmt.format(Date(i.timestamp))}] ${i.type.replace('_', ' ')}")
                    i.note?.let { append(": $it") }
                    appendLine()
                }
            }

            // Shared activities
            if (activities.isNotEmpty()) {
                appendLine()
                appendLine("Shared activities:")
                activities.take(10).forEach { a ->
                    append("- [${dateFmt.format(Date(a.timestamp))}] ${a.title}")
                    a.body?.let { append(": $it") }
                    appendLine()
                }
            }

            // Notes
            if (notes.isNotEmpty()) {
                appendLine()
                appendLine("Notes:")
                notes.take(15).forEach { n ->
                    appendLine("- [${dateFmt.format(Date(n.timestamp))}] ${n.body}")
                }
            }

            // Open tasks for this person
            val openTasks = tasks.filter { !it.isDone }
            if (openTasks.isNotEmpty()) {
                appendLine()
                appendLine("Open tasks:")
                openTasks.forEach { t ->
                    append("- ${t.title}")
                    t.dueAt?.let { append(" (due ${dateFmt.format(Date(it))})") }
                    appendLine()
                }
            }

            // Pending gifts
            val pendingGifts = gifts.filter { it.status != GiftStatus.GIVEN }
            if (pendingGifts.isNotEmpty()) {
                appendLine()
                appendLine("Gift tracking:")
                pendingGifts.forEach { g ->
                    append("- ${g.name} [${g.status.lowercase()}]")
                    g.occasion?.let { append(" for $it") }
                    appendLine()
                }
            }
        }

        emitAll(provider.process(context, Prompts.SUMMARIZE_SYSTEM))
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
