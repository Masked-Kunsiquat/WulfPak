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
    return try {
        val arr = JSONArray(this)
        (0 until arr.length()).mapNotNull { i ->
            try {
                val obj       = arr.getJSONObject(i)
                val personId  = obj.optString("personId", "")
                val callType  = obj.optString("callType", "")
                val timestamp = obj.optLong("timestamp", -1L)
                if (personId.isEmpty() || callType.isEmpty() || timestamp < 0L) return@mapNotNull null
                PendingCallStub(
                    personId        = personId,
                    personFirstName = obj.optString("personFirstName", ""),
                    callType        = callType,
                    durationSeconds = if (obj.isNull("durationSeconds")) null else obj.optInt("durationSeconds"),
                    timestamp       = timestamp,
                )
            } catch (_: Exception) { null }
        }
    } catch (_: Exception) { emptyList() }
}
