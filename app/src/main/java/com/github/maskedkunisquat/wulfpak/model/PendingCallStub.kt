package com.github.maskedkunisquat.wulfpak.model

import org.json.JSONArray
import org.json.JSONObject

data class PendingCallStub(
    val personId: String,
    val personFirstName: String,
    val callType: String,        // "INCOMING" | "OUTGOING" | "MISSED"
    val durationSeconds: Int?,
    val timestamp: Long,
)

fun PendingCallStub.toJson(): JSONObject = JSONObject().apply {
    put("personId", personId)
    put("personFirstName", personFirstName)
    put("callType", callType)
    if (durationSeconds != null) put("durationSeconds", durationSeconds) else put("durationSeconds", JSONObject.NULL)
    put("timestamp", timestamp)
}

fun List<PendingCallStub>.toJsonString(): String {
    val arr = JSONArray()
    forEach { arr.put(it.toJson()) }
    return arr.toString()
}

fun String.toPendingCallStubs(): List<PendingCallStub> {
    if (isBlank()) return emptyList()
    val arr = JSONArray(this)
    return (0 until arr.length()).map { i ->
        val obj = arr.getJSONObject(i)
        PendingCallStub(
            personId = obj.getString("personId"),
            personFirstName = obj.getString("personFirstName"),
            callType = obj.getString("callType"),
            durationSeconds = if (obj.isNull("durationSeconds")) null else obj.getInt("durationSeconds"),
            timestamp = obj.getLong("timestamp"),
        )
    }
}
