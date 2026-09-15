package com.locus.core.data.files

import android.content.Context
import android.net.Uri
import com.locus.core.domain.notes.Checksum
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class SafNoteFileWriterTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var fileSource: AndroidSafNoteFileSource
    private lateinit var treeUriStore: TreeUriStore
    private lateinit var writer: SafNoteFileWriter

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        fileSource = AndroidSafNoteFileSource(context)
        treeUriStore =
            object : TreeUriStore {
                private var uri: Uri? = null
                override val treeUriFlow = MutableStateFlow<Uri?>(null)

                override suspend fun getTreeUri(): Uri? = uri

                override suspend fun setTreeUri(uri: Uri) {
                    this.uri = uri
                    treeUriFlow.value = uri
                }
            }
        writer = SafNoteFileWriter(context, fileSource, treeUriStore)
    }

    @Test
    fun atomicWrite_toDirectFile_writesContentAndReturnsChecksum() =
        runTest {
            val targetFile = File(tempFolder.root, "test-note.md")
            val content = "# Test Note\n\nThis is content."
            val expectedChecksum = Checksum.sha256(content)

            val result = writer.atomicWrite("note-1", targetFile.absolutePath, content)

            assertTrue(result.isSuccess)
            val receipt = result.getOrNull()!!
            assertEquals("note-1", receipt.noteId)
            assertEquals(expectedChecksum, receipt.checksum)
            assertEquals(content, targetFile.readText(Charsets.UTF_8))

            // Ensure no leftover .tmp files
            val tmpFiles = tempFolder.root.listFiles { _, name -> name.endsWith(".tmp") }.orEmpty()
            assertTrue(tmpFiles.isEmpty())
        }

    @Test
    fun atomicWrite_overwritesExistingFile_atomically() =
        runTest {
            val targetFile = File(tempFolder.root, "overwrite-note.md")
            targetFile.writeText("initial content", Charsets.UTF_8)

            val newContent = "updated content with new checksum"
            val expectedChecksum = Checksum.sha256(newContent)

            val result = writer.atomicWrite("note-overwrite", targetFile.absolutePath, newContent)

            assertTrue(result.isSuccess)
            val receipt = result.getOrNull()!!
            assertEquals("note-overwrite", receipt.noteId)
            assertEquals(expectedChecksum, receipt.checksum)
            assertEquals(newContent, targetFile.readText(Charsets.UTF_8))
        }
}
