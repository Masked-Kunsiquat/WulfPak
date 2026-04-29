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
