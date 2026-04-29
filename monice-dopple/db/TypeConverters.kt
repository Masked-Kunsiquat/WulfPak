package com.yourapp.db

import androidx.room.TypeConverter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Room type converters for types used across the app.
 *
 * Add app-specific enum converters here as your schema grows.
 */
class AppTypeConverters {

    @TypeConverter
    fun fromUUID(uuid: UUID?): String? = uuid?.toString()

    @TypeConverter
    fun toUUID(uuidString: String?): UUID? = uuidString?.let { UUID.fromString(it) }

    /**
     * Serialises [FloatArray] to [ByteArray] using IEEE 754 float32 little-endian byte order.
     * Each float occupies exactly 4 bytes; a 384-dim embedding produces a 1536-byte BLOB.
     */
    @TypeConverter
    fun fromFloatArray(array: FloatArray?): ByteArray? {
        array ?: return null
        val buf = ByteBuffer.allocate(array.size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        buf.asFloatBuffer().put(array)
        return buf.array()
    }

    /**
     * Deserialises [ByteArray] (IEEE 754 float32 little-endian) back to [FloatArray].
     */
    @TypeConverter
    fun toFloatArray(bytes: ByteArray?): FloatArray? {
        bytes ?: return null
        val floatBuf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        return FloatArray(floatBuf.remaining()) { floatBuf.get() }
    }

    @TypeConverter
    fun fromStringList(value: List<String>?): String? = value?.let {
        org.json.JSONArray(it).toString()
    }

    @TypeConverter
    fun toStringList(value: String?): List<String>? = value?.let {
        val array = org.json.JSONArray(it)
        List(array.length()) { i -> array.getString(i) }
    }

    @TypeConverter
    fun fromUuidList(value: List<UUID>?): String? = value?.let {
        org.json.JSONArray(it.map { uuid -> uuid.toString() }).toString()
    }

    @TypeConverter
    fun toUuidList(value: String?): List<UUID>? = value?.let {
        val array = org.json.JSONArray(it)
        List(array.length()) { i -> UUID.fromString(array.getString(i)) }
    }
}
