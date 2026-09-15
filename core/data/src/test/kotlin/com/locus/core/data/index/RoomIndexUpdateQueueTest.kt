package com.locus.core.data.index

import android.content.Context
import androidx.room.Room
import com.locus.core.data.db.LocusDatabase
import com.locus.core.data.db.NoteDao
import com.locus.core.data.db.NoteIndexEntity
import com.locus.core.domain.notes.FlushReceipt
import com.locus.core.domain.notes.NoteType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class RoomIndexUpdateQueueTest {
    private lateinit var db: LocusDatabase
    private lateinit var noteDao: NoteDao
    private lateinit var queue: RoomIndexUpdateQueue

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        db =
            Room
                .inMemoryDatabaseBuilder(context, LocusDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        noteDao = db.noteDao()
        queue = RoomIndexUpdateQueue(noteDao)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun enqueue_whenNoteDoesNotExist_createsMinimalEntity() =
        runTest {
            val receipt =
                FlushReceipt(
                    noteId = "note-new",
                    checksum = "abc123hash",
                    flushedAt = 1726488000000L,
                )

            queue.enqueue(receipt)

            val entity = noteDao.getById("note-new")
            assertNotNull(entity)
            assertEquals("note-new", entity!!.id)
            assertEquals("abc123hash", entity.checksum)
            assertEquals(Instant.ofEpochMilli(1726488000000L), entity.modified)
            assertEquals(Instant.ofEpochMilli(1726488000000L), entity.created)
            assertEquals("", entity.title)
            assertEquals("", entity.bodyPreview)
        }

    @Test
    fun enqueue_whenNoteExists_updatesOnlyChecksumAndModified() =
        runTest {
            val existing =
                NoteIndexEntity(
                    id = "note-exist",
                    title = "Existing Title",
                    type = NoteType.CHECKLIST,
                    folderPath = "projects/locus",
                    pinned = true,
                    color = "#FF0000",
                    tags = listOf("todo", "urgent"),
                    created = Instant.ofEpochMilli(1700000000000L),
                    modified = Instant.ofEpochMilli(1700000000000L),
                    checksum = "oldhash",
                    bodyPreview = "- [ ] buy milk",
                )
            noteDao.upsert(existing)

            val receipt =
                FlushReceipt(
                    noteId = "note-exist",
                    checksum = "newhash456",
                    flushedAt = 1726500000000L,
                )

            queue.enqueue(receipt)

            val updated = noteDao.getById("note-exist")
            assertNotNull(updated)
            assertEquals("note-exist", updated!!.id)
            assertEquals("Existing Title", updated.title)
            assertEquals(NoteType.CHECKLIST, updated.type)
            assertEquals("projects/locus", updated.folderPath)
            assertEquals(true, updated.pinned)
            assertEquals("#FF0000", updated.color)
            assertEquals(listOf("todo", "urgent"), updated.tags)
            assertEquals(Instant.ofEpochMilli(1700000000000L), updated.created)
            assertEquals(Instant.ofEpochMilli(1726500000000L), updated.modified)
            assertEquals("newhash456", updated.checksum)
            assertEquals("- [ ] buy milk", updated.bodyPreview)
        }
}
