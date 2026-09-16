package com.locus.core.data.files

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.documentfile.provider.TestDocumentFile
import com.locus.core.domain.notes.FrontmatterParser
import com.locus.core.domain.notes.SnakeYamlCodec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import com.locus.core.domain.notes.Checksum
import com.locus.core.domain.notes.FlushReceipt
import com.locus.core.domain.notes.IndexUpdateQueue
import com.locus.core.domain.notes.NoteFileWriter
import com.locus.core.domain.notes.NoteFlushCoordinator
import com.locus.core.domain.time.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SafNoteRepositoryTest {
    private lateinit var parser: FrontmatterParser
    private val treeUri: Uri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ANotes")

    @Before
    fun setUp() {
        parser = FrontmatterParser(SnakeYamlCodec())
    }

    private class FakeSafNoteFileSource(
        val root: TestDocumentFile,
    ) : SafNoteFileSource {
        override fun getRootDocument(treeUri: Uri): DocumentFile = root

        override fun listMarkdownFiles(treeUri: Uri): List<DocumentFile> {
            val result = mutableListOf<DocumentFile>()

            fun collect(dir: DocumentFile) {
                for (child in dir.listFiles()) {
                    val name = child.name ?: continue
                    if (child.isDirectory && !name.startsWith(".")) {
                        collect(child)
                    } else if (child.isFile && name.endsWith(".md", ignoreCase = true)) {
                        result.add(child)
                    }
                }
            }
            collect(root)
            return result
        }

        override fun readText(doc: DocumentFile): String = (doc as? TestDocumentFile)?.content ?: ""

        override fun listFolders(treeUri: Uri): List<String> {
            val result = mutableListOf<String>()

            fun collect(
                dir: DocumentFile,
                path: String,
            ) {
                for (child in dir.listFiles()) {
                    val name = child.name ?: continue
                    if (child.isDirectory && !name.startsWith(".")) {
                        val childPath = if (path.isEmpty()) name else "$path/$name"
                        result.add(childPath)
                        collect(child, childPath)
                    }
                }
            }
            collect(root, "")
            return result.sorted()
        }
    }

    @Test
    fun externalMove_updatesFolderPathOnNextReadWithoutFrontmatterChange() =
        runTest {
            val root = TestDocumentFile(parent = null, docName = "Notes", isDir = true)
            val personalFolder = TestDocumentFile(parent = root, docName = "Personal", isDir = true)
            root.children.add(personalFolder)

            val workFolder = TestDocumentFile(parent = root, docName = "Work", isDir = true)
            root.children.add(workFolder)

            val rawContentWithoutFolder =
                """
                ---
                id: 0191ebc2-841e-7b28-b072-46ebc605cf52
                title: My Task Plan
                type: note
                created: 2026-09-16T10:00:00Z
                modified: 2026-09-16T10:00:00Z
                pinned: false
                ---
                Finish the presentation slides.
                """.trimIndent()

            val noteFile =
                TestDocumentFile(
                    parent = personalFolder,
                    docName = "My Task Plan.md",
                    isDir = false,
                    content = rawContentWithoutFolder,
                )
            personalFolder.children.add(noteFile)

            val fileSource = FakeSafNoteFileSource(root)
            val repository =
                SafNoteRepository(
                    fileSource = fileSource,
                    parser = parser,
                    initialTreeUri = treeUri,
                )

            // Read 1: Note is in Personal
            val firstRead = repository.observeAllNotes().first()
            assertEquals(1, firstRead.size)
            assertEquals("0191ebc2-841e-7b28-b072-46ebc605cf52", firstRead[0].id)
            assertEquals("Personal", firstRead[0].folderPath)

            // Simulate external move outside the app: moved to Work folder
            // The file's raw content (and frontmatter) is 100% UNCHANGED!
            noteFile.moveTo(workFolder)

            // Read 2: On next read, folderPath is recomputed from the new parent chain
            repository.refresh()
            val secondRead = repository.observeAllNotes().first()
            assertEquals(1, secondRead.size)
            assertEquals("0191ebc2-841e-7b28-b072-46ebc605cf52", secondRead[0].id)
            assertEquals("Work", secondRead[0].folderPath)
            assertEquals(rawContentWithoutFolder, noteFile.content)
        }

    @Test
    fun observeNotesInFolder_matchesRootAndSubfolders() =
        runTest {
            val root = TestDocumentFile(parent = null, docName = "Notes", isDir = true)
            val ideasFolder = TestDocumentFile(parent = root, docName = "Ideas", isDir = true)
            root.children.add(ideasFolder)

            val rootNote =
                TestDocumentFile(
                    parent = root,
                    docName = "Root Note.md",
                    isDir = false,
                    content =
                        """
                        ---
                        id: 0191ebc2-0000-0000-0000-000000000001
                        title: Root Note
                        type: note
                        ---
                        Root content
                        """.trimIndent(),
                )
            root.children.add(rootNote)

            val subNote =
                TestDocumentFile(
                    parent = ideasFolder,
                    docName = "Idea Note.md",
                    isDir = false,
                    content =
                        """
                        ---
                        id: 0191ebc2-0000-0000-0000-000000000002
                        title: Idea Note
                        type: note
                        ---
                        Idea content
                        """.trimIndent(),
                )
            ideasFolder.children.add(subNote)

            val fileSource = FakeSafNoteFileSource(root)
            val repository =
                SafNoteRepository(
                    fileSource = fileSource,
                    parser = parser,
                    initialTreeUri = treeUri,
                )

            // Query root folder with ""
            val rootNotesEmpty = repository.observeNotesInFolder("").first()
            assertEquals(1, rootNotesEmpty.size)
            assertEquals("0191ebc2-0000-0000-0000-000000000001", rootNotesEmpty[0].id)
            assertEquals("", rootNotesEmpty[0].folderPath)

            // Query root folder with "/"
            val rootNotesSlash = repository.observeNotesInFolder("/").first()
            assertEquals(1, rootNotesSlash.size)
            assertEquals("0191ebc2-0000-0000-0000-000000000001", rootNotesSlash[0].id)

            // Query subfolder with "Ideas"
            val ideasNotes = repository.observeNotesInFolder("Ideas").first()
            assertEquals(1, ideasNotes.size)
            assertEquals("0191ebc2-0000-0000-0000-000000000002", ideasNotes[0].id)
            assertEquals("Ideas", ideasNotes[0].folderPath)

            // Query subfolder with "/Ideas"
            val ideasNotesSlash = repository.observeNotesInFolder("/Ideas").first()
            assertEquals(1, ideasNotesSlash.size)
            assertEquals("0191ebc2-0000-0000-0000-000000000002", ideasNotesSlash[0].id)

            // Query non-existent folder
            val emptyNotes = repository.observeNotesInFolder("NonExistent").first()
            assertTrue(emptyNotes.isEmpty())
        }

    @Test
    fun readBody_stripsFrontmatterAndReturnsRawBody() =
        runTest {
            val root = TestDocumentFile(parent = null, docName = "Notes", isDir = true)
            val note =
                TestDocumentFile(
                    parent = root,
                    docName = "Doc.md",
                    isDir = false,
                    content =
                        """
                        ---
                        id: 0191ebc2-0000-0000-0000-000000000005
                        title: Title
                        type: note
                        ---
                        First line of body.
                        Second line of body.
                        """.trimIndent(),
                )
            root.children.add(note)

            val fileSource = FakeSafNoteFileSource(root)
            val repository =
                SafNoteRepository(
                    fileSource = fileSource,
                    parser = parser,
                    initialTreeUri = treeUri,
                )

            val body = repository.readBody("0191ebc2-0000-0000-0000-000000000005")
            assertEquals("First line of body.\nSecond line of body.", body.trim())
        }

    @Test
    fun listFolders_returnsSubfoldersExcludingHidden() =
        runTest {
            val root = TestDocumentFile(parent = null, docName = "Notes", isDir = true)
            val dirA = TestDocumentFile(parent = root, docName = "Personal", isDir = true)
            val dirB = TestDocumentFile(parent = dirA, docName = "Finances", isDir = true)
            dirA.children.add(dirB)
            root.children.add(dirA)

            val hiddenDir = TestDocumentFile(parent = root, docName = ".locus", isDir = true)
            root.children.add(hiddenDir)

            val fileSource = FakeSafNoteFileSource(root)
            val repository =
                SafNoteRepository(
                    fileSource = fileSource,
                    parser = parser,
                    initialTreeUri = treeUri,
                )

            val folders = repository.listFolders()
            assertEquals(listOf("Personal", "Personal/Finances"), folders)
        }

    @Test
    fun missingTreeUri_returnsEmptyListsWithoutCrashing() =
        runTest {
            val root = TestDocumentFile(parent = null, docName = "Notes", isDir = true)
            val fileSource = FakeSafNoteFileSource(root)
            val repository =
                SafNoteRepository(
                    fileSource = fileSource,
                    parser = parser,
                    initialTreeUri = null,
                )

            val notes = repository.observeAllNotes().first()
            assertTrue(notes.isEmpty())

            val folders = repository.listFolders()
            assertTrue(folders.isEmpty())
        }

    @Test
    fun setPinned_updatesFrontmatterAndEmitsNewState() =
        runTest {
            val root = TestDocumentFile(parent = null, docName = "Notes", isDir = true)
            val rawContent =
                """
                ---
                id: 0191ebc2-841e-7b28-b072-46ebc605cf52
                title: Pin Test Note
                type: note
                created: 2026-09-16T10:00:00Z
                modified: 2026-09-16T10:00:00Z
                pinned: false
                ---
                Some content here.
                """.trimIndent()

            val noteFile =
                TestDocumentFile(
                    parent = root,
                    docName = "Pin Test Note.md",
                    isDir = false,
                    content = rawContent,
                )
            root.children.add(noteFile)

            val fileSource = FakeSafNoteFileSource(root)
            val fileWriter =
                object : NoteFileWriter {
                    override suspend fun atomicWrite(
                        noteId: String,
                        path: String,
                        content: String,
                    ): Result<FlushReceipt> {
                        noteFile.content = content
                        return Result.success(
                            FlushReceipt(
                                noteId = noteId,
                                checksum = Checksum.sha256(content),
                                flushedAt = System.currentTimeMillis(),
                            ),
                        )
                    }
                }
            val dummyQueue =
                object : IndexUpdateQueue {
                    override suspend fun enqueue(receipt: FlushReceipt) = Unit
                }
            val testDispatchers =
                object : DispatcherProvider {
                    override val io: CoroutineDispatcher = Dispatchers.Unconfined
                    override val default: CoroutineDispatcher = Dispatchers.Unconfined
                    override val main: CoroutineDispatcher = Dispatchers.Unconfined
                    override val mainImmediate: CoroutineDispatcher = Dispatchers.Unconfined
                }
            val coordinator =
                NoteFlushCoordinator(
                    fileWriter = fileWriter,
                    indexQueue = dummyQueue,
                    dispatchers = testDispatchers,
                    scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
                )

            val repository =
                SafNoteRepository(
                    fileSource = fileSource,
                    parser = parser,
                    coordinator = coordinator,
                    initialTreeUri = treeUri,
                )

            // Initial state: not pinned
            val initial = repository.observeAllNotes().first()
            assertEquals(1, initial.size)
            assertEquals(false, initial[0].pinned)

            // Pin the note
            repository.setPinned("0191ebc2-841e-7b28-b072-46ebc605cf52", true)

            // Verify file content updated with frontmatter round-trip
            assertTrue(noteFile.content.contains("pinned: true"))

            // Verify repository flow emits updated state
            val afterPin = repository.observeAllNotes().first()
            assertEquals(1, afterPin.size)
            assertEquals(true, afterPin[0].pinned)

            // Unpin the note
            repository.setPinned("0191ebc2-841e-7b28-b072-46ebc605cf52", false)
            assertTrue(noteFile.content.contains("pinned: false"))
            val afterUnpin = repository.observeAllNotes().first()
            assertEquals(false, afterUnpin[0].pinned)
        }

    @Test
    fun setColor_updatesFrontmatterAndEmitsNewState() =
        runTest {
            val root = TestDocumentFile(parent = null, docName = "Notes", isDir = true)
            val rawContent =
                """
                ---
                id: 0191ebc2-841e-7b28-b072-46ebc605cf52
                title: Color Test Note
                type: note
                created: 2026-09-16T10:00:00Z
                modified: 2026-09-16T10:00:00Z
                pinned: false
                ---
                Some content here.
                """.trimIndent()

            val noteFile =
                TestDocumentFile(
                    parent = root,
                    docName = "Color Test Note.md",
                    isDir = false,
                    content = rawContent,
                )
            root.children.add(noteFile)

            val fileSource = FakeSafNoteFileSource(root)
            val fileWriter =
                object : NoteFileWriter {
                    override suspend fun atomicWrite(
                        noteId: String,
                        path: String,
                        content: String,
                    ): Result<FlushReceipt> {
                        noteFile.content = content
                        return Result.success(
                            FlushReceipt(
                                noteId = noteId,
                                checksum = Checksum.sha256(content),
                                flushedAt = System.currentTimeMillis(),
                            ),
                        )
                    }
                }
            val dummyQueue =
                object : IndexUpdateQueue {
                    override suspend fun enqueue(receipt: FlushReceipt) = Unit
                }
            val testDispatchers =
                object : DispatcherProvider {
                    override val io: CoroutineDispatcher = Dispatchers.Unconfined
                    override val default: CoroutineDispatcher = Dispatchers.Unconfined
                    override val main: CoroutineDispatcher = Dispatchers.Unconfined
                    override val mainImmediate: CoroutineDispatcher = Dispatchers.Unconfined
                }
            val coordinator =
                NoteFlushCoordinator(
                    fileWriter = fileWriter,
                    indexQueue = dummyQueue,
                    dispatchers = testDispatchers,
                    scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
                )

            val repository =
                SafNoteRepository(
                    fileSource = fileSource,
                    parser = parser,
                    coordinator = coordinator,
                    initialTreeUri = treeUri,
                )

            // Initial state: no color
            val initial = repository.observeAllNotes().first()
            assertEquals(1, initial.size)
            assertNull(initial[0].color)

            // Set color to Coral (#F28B82)
            repository.setColor("0191ebc2-841e-7b28-b072-46ebc605cf52", "#F28B82")

            // Verify file content updated with frontmatter round-trip
            assertTrue(noteFile.content.contains("#F28B82"))

            // Verify repository flow emits updated state
            val afterColor = repository.observeAllNotes().first()
            assertEquals(1, afterColor.size)
            assertEquals("#F28B82", afterColor[0].color)

            // Clear color (set to null)
            repository.setColor("0191ebc2-841e-7b28-b072-46ebc605cf52", null)
            val afterClear = repository.observeAllNotes().first()
            assertEquals(1, afterClear.size)
            assertNull(afterClear[0].color)
        }
}
