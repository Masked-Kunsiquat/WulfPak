package com.github.maskedkunisquat.wulfpak.core.data.db

import androidx.room.TypeConverter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

class AppTypeConverters {

    @TypeConverter
    fun fromUUID(uuid: UUID?): String? = uuid?.toString()

    @TypeConverter
    fun toUUID(value: String?): UUID? = value?.let { UUID.fromString(it) }

    // 384-dim embedding: each float = 4 bytes → 1536-byte BLOB, little-endian
    @TypeConverter
    fun fromFloatArray(array: FloatArray?): ByteArray? {
        array ?: return null
        val buf = ByteBuffer.allocate(array.size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        buf.asFloatBuffer().put(array)
        return buf.array()
    }

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
}
