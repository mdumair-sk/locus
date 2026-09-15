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
}
