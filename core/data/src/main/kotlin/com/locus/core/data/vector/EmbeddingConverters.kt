package com.locus.core.data.vector

import androidx.room.TypeConverter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class EmbeddingConverters {
    companion object {
        private const val BYTES_PER_FLOAT = 4
    }

    @TypeConverter
    fun fromFloatArray(array: FloatArray?): ByteArray? {
        if (array == null) return null
        val buffer =
            ByteBuffer.allocate(array.size * BYTES_PER_FLOAT).order(ByteOrder.LITTLE_ENDIAN)
        buffer.asFloatBuffer().put(array)
        return buffer.array()
    }

    @TypeConverter
    fun toFloatArray(bytes: ByteArray?): FloatArray? {
        if (bytes == null) return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val floatBuffer = buffer.asFloatBuffer()
        val floats = FloatArray(floatBuffer.remaining())
        floatBuffer.get(floats)
        return floats
    }
}
