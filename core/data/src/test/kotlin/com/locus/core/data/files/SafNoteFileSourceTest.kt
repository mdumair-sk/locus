package com.locus.core.data.files

import android.content.Context
import android.net.Uri
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SafNoteFileSourceTest {
    private lateinit var context: Context
    private lateinit var source: AndroidSafNoteFileSource

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        source = AndroidSafNoteFileSource(context)
    }

    @Test
    fun invalidTreeUri_returnsEmptyListsGracefully() {
        val invalidUri = Uri.parse("content://nonexistent.provider/tree/123")
        val files = source.listMarkdownFiles(invalidUri)
        assertTrue(files.isEmpty())

        val folders = source.listFolders(invalidUri)
        assertTrue(folders.isEmpty())
    }
}
