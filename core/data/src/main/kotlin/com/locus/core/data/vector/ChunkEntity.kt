package com.locus.core.data.vector

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chunks",
    indices =
        [
            Index(value = ["noteId"]),
        ],
)
data class ChunkEntity(
    @PrimaryKey val chunkId: String,
    val noteId: String,
    val headingPathJson: String,
    val text: String,
    val embedding: FloatArray,
    val embeddingModelId: String,
    val sourceChecksum: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ChunkEntity

        if (chunkId != other.chunkId) return false
        if (noteId != other.noteId) return false
        if (headingPathJson != other.headingPathJson) return false
        if (text != other.text) return false
        if (!embedding.contentEquals(other.embedding)) return false
        if (embeddingModelId != other.embeddingModelId) return false
        if (sourceChecksum != other.sourceChecksum) return false

        return true
    }

    override fun hashCode(): Int {
        var result = chunkId.hashCode()
        result = 31 * result + noteId.hashCode()
        result = 31 * result + headingPathJson.hashCode()
        result = 31 * result + text.hashCode()
        result = 31 * result + embedding.contentHashCode()
        result = 31 * result + embeddingModelId.hashCode()
        result = 31 * result + sourceChecksum.hashCode()
        return result
    }
}

data class ChunkMetadataTuple(
    val sourceChecksum: String,
    val embeddingModelId: String,
)

data class ChunkEmbeddingTuple(
    val chunkId: String,
    val noteId: String,
    val embedding: FloatArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ChunkEmbeddingTuple

        if (chunkId != other.chunkId) return false
        if (noteId != other.noteId) return false
        if (!embedding.contentEquals(other.embedding)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = chunkId.hashCode()
        result = 31 * result + noteId.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}
