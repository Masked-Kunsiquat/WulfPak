package com.github.maskedkunisquat.wulfpak.ui.common

import com.github.maskedkunisquat.wulfpak.core.data.calculateAge
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun String.toDisplayLabel(): String =
    replace("_", " ").split(" ")
        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

fun Long.toDisplayDate(): String =
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(this))

fun Long.toRelativeDisplay(): String {
    val nowCal  = Calendar.getInstance()
    val thenCal = Calendar.getInstance().apply { timeInMillis = this@toRelativeDisplay }

    // Use calendar fields instead of ms division to handle DST spring-forward (23h day).
    val days: Int = if (nowCal.get(Calendar.YEAR) == thenCal.get(Calendar.YEAR)) {
        nowCal.get(Calendar.DAY_OF_YEAR) - thenCal.get(Calendar.DAY_OF_YEAR)
    } else {
        val cursor = thenCal.clone() as Calendar
        cursor[Calendar.HOUR_OF_DAY] = 0; cursor[Calendar.MINUTE] = 0
        cursor[Calendar.SECOND] = 0;      cursor[Calendar.MILLISECOND] = 0
        val end = nowCal.clone() as Calendar
        end[Calendar.HOUR_OF_DAY] = 0; end[Calendar.MINUTE] = 0
        end[Calendar.SECOND] = 0;      end[Calendar.MILLISECOND] = 0
        var count = 0
        while (cursor.before(end) && count < 400) { cursor.add(Calendar.DAY_OF_MONTH, 1); count++ }
        count
    }

    return when {
        days == 0  -> "today"
        days == 1  -> "yesterday"
        days < 7   -> "$days days ago"
        days < 30  -> "${days / 7}w ago"
        days < 365 -> "${days / 30}mo ago"
        else       -> SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(Date(this))
    }
}

fun Long.birthYearIsKnown(): Boolean =
    java.util.Calendar.getInstance().apply { timeInMillis = this@birthYearIsKnown }
        .get(java.util.Calendar.YEAR) != 1900

fun calculateAge(birthdayMs: Long, asOfMs: Long = System.currentTimeMillis()): Int =
    birthdayMs.calculateAge(asOfMs)
