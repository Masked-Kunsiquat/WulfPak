package com.github.maskedkunisquat.wulfpak.core.data

import java.util.Calendar

fun Long.calculateAge(asOfMs: Long = System.currentTimeMillis()): Int {
    val cal = Calendar.getInstance()
    cal.timeInMillis = asOfMs
    val nowYear  = cal.get(Calendar.YEAR)
    val nowMonth = cal.get(Calendar.MONTH)
    val nowDay   = cal.get(Calendar.DAY_OF_MONTH)
    cal.timeInMillis = this
    val birthYear  = cal.get(Calendar.YEAR)
    val birthMonth = cal.get(Calendar.MONTH)
    val birthDay   = cal.get(Calendar.DAY_OF_MONTH)
    var age = nowYear - birthYear
    if (nowMonth < birthMonth || (nowMonth == birthMonth && nowDay < birthDay)) age--
    return age
}
