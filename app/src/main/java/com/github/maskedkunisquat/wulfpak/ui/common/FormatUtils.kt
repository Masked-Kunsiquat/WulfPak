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
    fun Long.midnight(): Long = Calendar.getInstance().apply {
        timeInMillis = this@midnight
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0);      set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val days = ((System.currentTimeMillis().midnight() - this.midnight()) / 86_400_000L).toInt()
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
