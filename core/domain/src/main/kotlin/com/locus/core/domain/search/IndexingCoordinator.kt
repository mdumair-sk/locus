package com.locus.core.domain.search

import com.locus.core.domain.notes.Note
import com.locus.core.domain.notes.NoteRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IndexingCoordinator(
    private val embeddingGateway: EmbeddingGateway,
    private val chunkRepository: ChunkRepository,
    private val noteRepository: NoteRepository,
    private val noteChunker: NoteChunker = NoteChunker(),
    initialModelId: String = DEFAULT_MODEL_ID,
) {
    @Inject
    constructor(
        embeddingGateway: EmbeddingGateway,
        chunkRepository: ChunkRepository,
        noteRepository: NoteRepository,
    ) : this(
        embeddingGateway = embeddingGateway,
        chunkRepository = chunkRepository,
        noteRepository = noteRepository,
        noteChunker = NoteChunker(),
        initialModelId = DEFAULT_MODEL_ID,
    )

    companion object {
        const val DEFAULT_MODEL_ID = "embeddinggemma-300M-Q8_0.gguf"
    }

    var currentModelId: String = initialModelId
        private set

    suspend fun reindexIfNeeded(
        note: Note,
        body: String,
    ) {
        val existing = chunkRepository.getMetadata(note.id)
        if (existing != null &&
            existing.sourceChecksum == note.checksum &&
            existing.embeddingModelId == currentModelId
        ) {
            return
        }

        val chunks = noteChunker.chunk(note.id, note.title, body)
        val embeddedChunks =
            chunks.map { chunk ->
                val embedding = embeddingGateway.embed(chunk.text)
                EmbeddedChunk(
                    chunkId = "${note.id}_${chunk.index}",
                    noteId = note.id,
                    headingPath = chunk.headingPath,
                    text = chunk.text,
                    embedding = embedding,
                    embeddingModelId = currentModelId,
                    sourceChecksum = note.checksum,
                )
            }

        chunkRepository.replaceChunksForNote(note.id, embeddedChunks)
    }

    suspend fun reindexAllForModelChange(newModelId: String) {
        currentModelId = newModelId
        val notes = noteRepository.observeAllNotes().first()
        for (note in notes) {
            val body = noteRepository.readBody(note.id)
            reindexIfNeeded(note, body)
        }
    }
}
