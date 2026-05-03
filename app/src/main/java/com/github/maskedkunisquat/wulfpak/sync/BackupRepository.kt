package com.github.maskedkunisquat.wulfpak.sync

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import java.io.InputStream
import com.github.maskedkunisquat.wulfpak.core.data.AppDatabase
import com.github.maskedkunisquat.wulfpak.core.data.entity.Activity
import com.github.maskedkunisquat.wulfpak.core.data.entity.ActivityParticipant
import com.github.maskedkunisquat.wulfpak.core.data.entity.ContactDetail
import com.github.maskedkunisquat.wulfpak.core.data.entity.Gift
import com.github.maskedkunisquat.wulfpak.core.data.entity.GiftStatus
import com.github.maskedkunisquat.wulfpak.core.data.entity.Interaction
import com.github.maskedkunisquat.wulfpak.core.data.entity.InteractionParticipant
import com.github.maskedkunisquat.wulfpak.core.data.entity.LifeEvent
import com.github.maskedkunisquat.wulfpak.core.data.entity.Note
import com.github.maskedkunisquat.wulfpak.core.data.entity.Person
import com.github.maskedkunisquat.wulfpak.core.data.entity.PersonRelationship
import com.github.maskedkunisquat.wulfpak.core.data.entity.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class BackupRepository(private val db: AppDatabase) {

    data class ImportResult(val personCount: Int)

    suspend fun export(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val root = JSONObject().apply {
            put("version", 8)
            put("exportedAt", System.currentTimeMillis())
            put("persons",                 db.personDao().getAllOnce().serializePersons())
            put("personRelationships",     db.personRelationshipDao().getAllOnce().serializeRelationships())
            put("contactDetails",          db.contactDetailDao().getAllOnce().serializeContactDetails())
            put("interactions",            db.interactionDao().getAllOnce().serializeInteractions())
            put("interactionParticipants", db.interactionDao().getAllParticipants().serializeInteractionParticipants())
            put("notes",                   db.noteDao().getAllOnce().serializeNotes())
            put("lifeEvents",              db.lifeEventDao().getAll().serializeLifeEvents())
            put("activities",              db.activityDao().getAllOnce().serializeActivities())
            put("activityParticipants",    db.activityDao().getAllParticipants().serializeActivityParticipants())
            put("gifts",                   db.giftDao().getAllOnce().serializeGifts())
            put("tasks",                   db.taskDao().getAllOnce().serializeTasks())
        }
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.writer().use { it.write(root.toString(2)) }
        } ?: error("Cannot open output stream for backup")
    }

    suspend fun import(context: Context, uri: Uri): ImportResult {
        val stream = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri) ?: error("Cannot open backup file")
        }
        return stream.use { importFromStream(it) }
    }

    suspend fun importFromStream(stream: InputStream): ImportResult = withContext(Dispatchers.IO) {
        val json = stream.bufferedReader().readText()
        val root = JSONObject(json)

        val persons        = root.getJSONArray("persons").mapObjects            { it.toPerson() }
        val relationships  = root.getJSONArray("personRelationships").mapObjects{ it.toRelationship() }
        val contactDetails = root.getJSONArray("contactDetails").mapObjects     { it.toContactDetail() }
        val interactions   = root.getJSONArray("interactions").mapObjects       { it.toInteraction() }
        val iParticipants  = root.getJSONArray("interactionParticipants").mapObjects { it.toInteractionParticipant() }
        val notes          = root.getJSONArray("notes").mapObjects              { it.toNote() }
        val lifeEvents     = root.getJSONArray("lifeEvents").mapObjects         { it.toLifeEvent() }
        val activities     = root.getJSONArray("activities").mapObjects         { it.toActivity() }
        val aParticipants  = root.getJSONArray("activityParticipants").mapObjects { it.toActivityParticipant() }
        val gifts          = root.getJSONArray("gifts").mapObjects              { it.toGift() }
        val tasks          = root.getJSONArray("tasks").mapObjects              { it.toTask() }

        db.withTransaction {
            val sqlDb = db.openHelper.writableDatabase
            sqlDb.execSQL("DELETE FROM interaction_participants")
            sqlDb.execSQL("DELETE FROM activity_participants")
            sqlDb.execSQL("DELETE FROM person_relationships")
            sqlDb.execSQL("DELETE FROM contact_details")
            sqlDb.execSQL("DELETE FROM life_events")
            sqlDb.execSQL("DELETE FROM gifts")
            sqlDb.execSQL("DELETE FROM notes")
            sqlDb.execSQL("DELETE FROM tasks")
            sqlDb.execSQL("DELETE FROM interactions")
            sqlDb.execSQL("DELETE FROM activities")
            sqlDb.execSQL("DELETE FROM persons")

            persons.forEach        { db.personDao().insert(it) }
            relationships.forEach  { db.personRelationshipDao().insert(it) }
            contactDetails.forEach { db.contactDetailDao().insert(it) }
            interactions.forEach   { db.interactionDao().insert(it) }
            iParticipants.forEach  { db.interactionDao().insertParticipant(it) }
            notes.forEach          { db.noteDao().insert(it) }
            lifeEvents.forEach     { db.lifeEventDao().insert(it) }
            activities.forEach     { db.activityDao().insert(it) }
            aParticipants.forEach  { db.activityDao().insertParticipant(it) }
            gifts.forEach          { db.giftDao().insert(it) }
            tasks.forEach          { db.taskDao().insert(it) }
        }

        ImportResult(persons.size)
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private inline fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
        (0 until length()).map { transform(getJSONObject(it)) }

    private fun JSONObject.uuid(key: String): UUID = UUID.fromString(getString(key))
    private fun JSONObject.uuidOrNull(key: String): UUID? = if (has(key) && !isNull(key)) UUID.fromString(getString(key)) else null
    private fun JSONObject.longOrNull(key: String): Long? = if (has(key) && !isNull(key)) getLong(key) else null
    private fun JSONObject.intOrNull(key: String): Int? = if (has(key) && !isNull(key)) getInt(key) else null
    private fun JSONObject.floatOrNull(key: String): Float? = if (has(key) && !isNull(key)) getDouble(key).toFloat() else null
    private fun JSONObject.strOrNull(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null

    // ── Serializers ───────────────────────────────────────────────────────

    private fun List<Person>.serializePersons() = JSONArray().also { arr ->
        forEach { p ->
            arr.put(JSONObject().apply {
                put("id", p.id.toString())
                put("firstName", p.firstName)
                putOpt("lastName", p.lastName)
                putOpt("nickname", p.nickname)
                put("relationLabel", p.relationLabel)
                put("isFavorite", p.isFavorite)
                putOpt("lastContactedAt", p.lastContactedAt)
                put("interactionCount", p.interactionCount)
                putOpt("closenessRating", p.closenessRating)
                putOpt("company", p.company)
                putOpt("jobTitle", p.jobTitle)
                putOpt("cachedSummary", p.cachedSummary)
                putOpt("summaryGeneratedAt", p.summaryGeneratedAt)
                putOpt("closenessScore", p.closenessScore?.toDouble())
                put("isMe", p.isMe)
            })
        }
    }

    private fun List<PersonRelationship>.serializeRelationships() = JSONArray().also { arr ->
        forEach { r ->
            arr.put(JSONObject().apply {
                put("personAId", r.personAId.toString())
                put("personBId", r.personBId.toString())
                put("label", r.label)
                put("category", r.category)
                putOpt("relType", r.relType)
            })
        }
    }

    private fun List<ContactDetail>.serializeContactDetails() = JSONArray().also { arr ->
        forEach { d ->
            arr.put(JSONObject().apply {
                put("id", d.id.toString())
                put("personId", d.personId.toString())
                put("type", d.type)
                put("label", d.label)
                put("value", d.value)
            })
        }
    }

    private fun List<Interaction>.serializeInteractions() = JSONArray().also { arr ->
        forEach { i ->
            arr.put(JSONObject().apply {
                put("id", i.id.toString())
                put("timestamp", i.timestamp)
                put("type", i.type)
                putOpt("durationSeconds", i.durationSeconds)
                putOpt("note", i.note)
                // embedding excluded — EmbeddingWorker regenerates it
            })
        }
    }

    private fun List<InteractionParticipant>.serializeInteractionParticipants() = JSONArray().also { arr ->
        forEach { ip ->
            arr.put(JSONObject().apply {
                put("interactionId", ip.interactionId.toString())
                put("personId", ip.personId.toString())
            })
        }
    }

    private fun List<Note>.serializeNotes() = JSONArray().also { arr ->
        forEach { n ->
            arr.put(JSONObject().apply {
                put("id", n.id.toString())
                putOpt("personId", n.personId?.toString())
                put("timestamp", n.timestamp)
                put("body", n.body)
                // embedding excluded
            })
        }
    }

    private fun List<LifeEvent>.serializeLifeEvents() = JSONArray().also { arr ->
        forEach { e ->
            arr.put(JSONObject().apply {
                put("id", e.id.toString())
                put("personId", e.personId.toString())
                put("eventType", e.eventType)
                put("date", e.date)
                put("isRecurring", e.isRecurring)
                putOpt("note", e.note)
            })
        }
    }

    private fun List<Activity>.serializeActivities() = JSONArray().also { arr ->
        forEach { a ->
            arr.put(JSONObject().apply {
                put("id", a.id.toString())
                put("timestamp", a.timestamp)
                put("title", a.title)
                putOpt("body", a.body)
                // embedding excluded
            })
        }
    }

    private fun List<ActivityParticipant>.serializeActivityParticipants() = JSONArray().also { arr ->
        forEach { ap ->
            arr.put(JSONObject().apply {
                put("activityId", ap.activityId.toString())
                put("personId", ap.personId.toString())
            })
        }
    }

    private fun List<Gift>.serializeGifts() = JSONArray().also { arr ->
        forEach { g ->
            arr.put(JSONObject().apply {
                put("id", g.id.toString())
                put("personId", g.personId.toString())
                put("name", g.name)
                putOpt("occasion", g.occasion)
                put("status", g.status)
                putOpt("note", g.note)
            })
        }
    }

    private fun List<Task>.serializeTasks() = JSONArray().also { arr ->
        forEach { t ->
            arr.put(JSONObject().apply {
                put("id", t.id.toString())
                putOpt("personId", t.personId?.toString())
                put("title", t.title)
                putOpt("dueAt", t.dueAt)
                put("isDone", t.isDone)
            })
        }
    }

    // ── Deserializers ─────────────────────────────────────────────────────

    private fun JSONObject.toPerson() = Person(
        id                 = uuid("id"),
        firstName          = getString("firstName"),
        lastName           = strOrNull("lastName"),
        nickname           = strOrNull("nickname"),
        relationLabel      = getString("relationLabel"),
        isFavorite         = optBoolean("isFavorite", false),
        lastContactedAt    = longOrNull("lastContactedAt"),
        interactionCount   = optInt("interactionCount", 0),
        closenessRating    = intOrNull("closenessRating"),
        company            = strOrNull("company"),
        jobTitle           = strOrNull("jobTitle"),
        cachedSummary      = strOrNull("cachedSummary"),
        summaryGeneratedAt = longOrNull("summaryGeneratedAt"),
        closenessScore     = floatOrNull("closenessScore"),
        isMe               = optBoolean("isMe", false),
    )

    private fun JSONObject.toRelationship() = PersonRelationship(
        personAId = uuid("personAId"),
        personBId = uuid("personBId"),
        label     = getString("label"),
        category  = optString("category", "OTHER"),
        relType   = strOrNull("relType"),
    )

    private fun JSONObject.toContactDetail() = ContactDetail(
        id       = uuid("id"),
        personId = uuid("personId"),
        type     = getString("type"),
        label    = getString("label"),
        value    = getString("value"),
    )

    private fun JSONObject.toInteraction() = Interaction(
        id              = uuid("id"),
        timestamp       = getLong("timestamp"),
        type            = getString("type"),
        durationSeconds = intOrNull("durationSeconds"),
        note            = strOrNull("note"),
    )

    private fun JSONObject.toInteractionParticipant() = InteractionParticipant(
        interactionId = uuid("interactionId"),
        personId      = uuid("personId"),
    )

    private fun JSONObject.toNote() = Note(
        id        = uuid("id"),
        personId  = uuidOrNull("personId"),
        timestamp = getLong("timestamp"),
        body      = getString("body"),
    )

    private fun JSONObject.toLifeEvent() = LifeEvent(
        id          = uuid("id"),
        personId    = uuid("personId"),
        eventType   = getString("eventType"),
        date        = getLong("date"),
        isRecurring = optBoolean("isRecurring", false),
        note        = strOrNull("note"),
    )

    private fun JSONObject.toActivity() = Activity(
        id        = uuid("id"),
        timestamp = getLong("timestamp"),
        title     = getString("title"),
        body      = strOrNull("body"),
    )

    private fun JSONObject.toActivityParticipant() = ActivityParticipant(
        activityId = uuid("activityId"),
        personId   = uuid("personId"),
    )

    private fun JSONObject.toGift() = Gift(
        id       = uuid("id"),
        personId = uuid("personId"),
        name     = getString("name"),
        occasion = strOrNull("occasion"),
        status   = optString("status", GiftStatus.IDEA),
        note     = strOrNull("note"),
    )

    private fun JSONObject.toTask() = Task(
        id       = uuid("id"),
        personId = uuidOrNull("personId"),
        title    = getString("title"),
        dueAt    = longOrNull("dueAt"),
        isDone   = optBoolean("isDone", false),
    )
}
