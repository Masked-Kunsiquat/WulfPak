package com.github.maskedkunisquat.wulfpak.sync

import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import com.github.maskedkunisquat.wulfpak.core.data.AppDatabase
import com.github.maskedkunisquat.wulfpak.core.data.entity.LifeEvent
import com.github.maskedkunisquat.wulfpak.core.data.entity.LifeEventType
import java.util.Calendar
import java.util.TimeZone

class CalendarBridge(private val db: AppDatabase) {

    data class SyncResult(val added: Int, val skipped: Int)

    suspend fun sync(context: Context): SyncResult {
        val calendarId = findOrCreateWulfPakCalendar(context) ?: return SyncResult(0, 0)
        val events = db.lifeEventDao().getAll()

        var added = 0
        var skipped = 0

        for (event in events) {
            val person = db.personDao().getById(event.personId) ?: continue
            val personName = listOfNotNull(person.firstName, person.lastName).joinToString(" ")
            val title = buildTitle(event.eventType, personName)
            val dtStart = normalizeToDayUtcMs(event.date)

            if (isAlreadySynced(context, calendarId, title, dtStart)) {
                skipped++
                continue
            }

            context.contentResolver.insert(
                CalendarContract.Events.CONTENT_URI,
                buildContentValues(calendarId, event, title, dtStart),
            )
            added++
        }

        return SyncResult(added, skipped)
    }

    private fun findOrCreateWulfPakCalendar(context: Context): Long? {
        val cr = context.contentResolver

        // Return existing WulfPak calendar if present
        cr.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID),
            "${CalendarContract.Calendars.ACCOUNT_NAME} = ? AND ${CalendarContract.Calendars.ACCOUNT_TYPE} = ?",
            arrayOf(CALENDAR_ACCOUNT, CalendarContract.ACCOUNT_TYPE_LOCAL),
            null,
        )?.use { c ->
            if (c.moveToFirst()) return c.getLong(0)
        }

        // Create the WulfPak local calendar; sync adapter URI is required for calendar insertion
        val insertUri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, CALENDAR_ACCOUNT)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            .build()

        val cv = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, CALENDAR_ACCOUNT)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.NAME, CALENDAR_ACCOUNT)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CALENDAR_ACCOUNT)
            put(CalendarContract.Calendars.CALENDAR_COLOR, 0xFF6750A4.toInt()) // Material You primary
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, CALENDAR_ACCOUNT)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            put(CalendarContract.Calendars.VISIBLE, 1)
        }

        val uri = cr.insert(insertUri, cv) ?: return null
        return uri.lastPathSegment?.toLongOrNull()
    }

    private fun isAlreadySynced(context: Context, calendarId: Long, title: String, dtStart: Long): Boolean {
        val projection = arrayOf(CalendarContract.Events._ID)
        val selection = "${CalendarContract.Events.CALENDAR_ID} = ? AND " +
                "${CalendarContract.Events.TITLE} = ? AND " +
                "${CalendarContract.Events.DTSTART} = ?"
        context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            arrayOf(calendarId.toString(), title, dtStart.toString()),
            null,
        )?.use { c -> return c.count > 0 }
        return false
    }

    private fun buildContentValues(
        calendarId: Long,
        event: LifeEvent,
        title: String,
        dtStart: Long,
    ): ContentValues = ContentValues().apply {
        put(CalendarContract.Events.CALENDAR_ID, calendarId)
        put(CalendarContract.Events.TITLE, title)
        put(CalendarContract.Events.DESCRIPTION, event.note ?: "")
        put(CalendarContract.Events.DTSTART, dtStart)
        put(CalendarContract.Events.ALL_DAY, 1)
        put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")

        if (event.isRecurring) {
            // DTEND must not be set alongside RRULE; use DURATION for all-day recurring events
            put(CalendarContract.Events.RRULE, "FREQ=YEARLY")
            put(CalendarContract.Events.DURATION, "P1D")
        } else {
            put(CalendarContract.Events.DTEND, dtStart + DAY_MS)
        }
    }

    private fun normalizeToDayUtcMs(epochMs: Long): Long {
        val local = Calendar.getInstance().apply { timeInMillis = epochMs }
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun buildTitle(eventType: String, personName: String): String = when (eventType) {
        LifeEventType.BIRTHDAY    -> "$personName's Birthday"
        LifeEventType.ANNIVERSARY -> "$personName's Anniversary"
        LifeEventType.GRADUATION  -> "$personName's Graduation"
        LifeEventType.JOB_CHANGE  -> "$personName's New Job"
        LifeEventType.MOVED       -> "$personName Moved"
        LifeEventType.DEATH       -> "$personName — Remembrance"
        else -> "$personName — ${eventType.replace('_', ' ').replaceFirstChar { it.uppercaseChar() }}"
    }

    companion object {
        private const val DAY_MS = 24 * 60 * 60 * 1000L
        private const val CALENDAR_ACCOUNT = "WulfPak"
    }
}
