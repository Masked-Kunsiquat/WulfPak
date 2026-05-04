package com.github.maskedkunisquat.wulfpak.core.data

fun normalizePhone(raw: String): String {
    val digits = raw.filter { it.isDigit() }
    return if (digits.length == 11 && digits.startsWith("1")) digits.drop(1) else digits
}
