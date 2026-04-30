package com.github.maskedkunisquat.wulfpak.ui.common

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun String.toDisplayLabel(): String =
    replace("_", " ").split(" ")
        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

fun Long.toDisplayDate(): String =
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(this))

fun Long.toRelativeDisplay(): String {
    val days = ((System.currentTimeMillis() - this) / 86_400_000L).toInt()
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

fun calculateAge(birthdayMs: Long, asOfMs: Long = System.currentTimeMillis()): Int {
    val cal = java.util.Calendar.getInstance()
    cal.timeInMillis = asOfMs
    val nowYear  = cal.get(java.util.Calendar.YEAR)
    val nowMonth = cal.get(java.util.Calendar.MONTH)
    val nowDay   = cal.get(java.util.Calendar.DAY_OF_MONTH)
    cal.timeInMillis = birthdayMs
    val birthYear  = cal.get(java.util.Calendar.YEAR)
    val birthMonth = cal.get(java.util.Calendar.MONTH)
    val birthDay   = cal.get(java.util.Calendar.DAY_OF_MONTH)
    var age = nowYear - birthYear
    if (nowMonth < birthMonth || (nowMonth == birthMonth && nowDay < birthDay)) age--
    return age
}
