package com.locus.core.data.index

import com.locus.core.data.db.NoteDao
import com.locus.core.data.db.NoteIndexEntity
import com.locus.core.domain.notes.FlushReceipt
import com.locus.core.domain.notes.IndexUpdateQueue
import com.locus.core.domain.notes.NoteType
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed implementation of [IndexUpdateQueue].
 *
 * For Prompt 7 (Phase 0), this queue upserts a minimal [NoteIndexEntity] row keyed by
 * [FlushReceipt.noteId], updating only [NoteIndexEntity.checksum] and [NoteIndexEntity.modified].
 *
 * Dependency note: Full chunk generation, FTS indexing, and embedding vector enqueue arrive in
 * Prompt 28. Extend [enqueue] at that point to enqueue chunking and embedding background jobs
 * after the file flush receipt is received.
 */
@Singleton
class RoomIndexUpdateQueue
    @Inject
    constructor(
        private val noteDao: NoteDao,
    ) : IndexUpdateQueue {
        override suspend fun enqueue(receipt: FlushReceipt) {
            val modifiedInstant = Instant.ofEpochMilli(receipt.flushedAt)
            val existing = noteDao.getById(receipt.noteId)
            val entity =
                if (existing != null) {
                    existing.copy(
                        checksum = receipt.checksum,
                        modified = modifiedInstant,
                    )
                } else {
                    NoteIndexEntity(
                        id = receipt.noteId,
                        title = "",
                        type = NoteType.NOTE,
                        folderPath = "",
                        pinned = false,
                        color = null,
                        tags = emptyList(),
                        created = modifiedInstant,
                        modified = modifiedInstant,
                        checksum = receipt.checksum,
                        bodyPreview = "",
                    )
                }
            noteDao.upsert(entity)
        }
    }
