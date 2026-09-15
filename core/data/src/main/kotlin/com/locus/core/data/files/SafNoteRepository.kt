package com.locus.core.data.files

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.locus.core.domain.notes.FileFallbackMetadata
import com.locus.core.domain.notes.FrontmatterParser
import com.locus.core.domain.notes.Note
import com.locus.core.domain.notes.NoteRepository
import com.locus.core.domain.notes.toDomain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SafNoteRepository
    @Inject
    constructor(
        private val fileSource: SafNoteFileSource,
        private val parser: FrontmatterParser,
        private val treeUriStore: TreeUriStore,
    ) : NoteRepository {
        constructor(
            fileSource: SafNoteFileSource,
            parser: FrontmatterParser,
            initialTreeUri: Uri? = null,
        ) : this(
            fileSource = fileSource,
            parser = parser,
            treeUriStore =
                object : TreeUriStore {
                    private var uri: Uri? = initialTreeUri
                    override val treeUriFlow = MutableStateFlow(initialTreeUri)

                    override suspend fun getTreeUri(): Uri? = uri

                    override suspend fun setTreeUri(uri: Uri) {
                        this.uri = uri
                        treeUriFlow.value = uri
                    }
                },
        ) {
            overrideTreeUri = initialTreeUri
        }

        private val refreshTrigger = MutableStateFlow(0L)
        private val noteIdToDoc = ConcurrentHashMap<String, DocumentFile>()
        private var overrideTreeUri: Uri? = null

        fun refresh() {
            refreshTrigger.value = System.currentTimeMillis()
        }

        fun setOverrideTreeUri(uri: Uri?) {
            overrideTreeUri = uri
            refresh()
        }

        private suspend fun getEffectiveTreeUri(): Uri? = overrideTreeUri ?: treeUriStore.getTreeUri()

        override fun observeAllNotes(): Flow<List<Note>> =
            combine(refreshTrigger, treeUriStore.treeUriFlow) { _, _ ->
                loadAllNotes()
            }

        override fun observeNotesInFolder(folderPath: String): Flow<List<Note>> {
            val normalizedTarget = normalizeFolderPath(folderPath)
            return observeAllNotes().map { notes ->
                notes.filter { normalizeFolderPath(it.folderPath) == normalizedTarget }
            }
        }

        override suspend fun readBody(noteId: String): String =
            withContext(Dispatchers.IO) {
                val treeUri =
                    getEffectiveTreeUri()
                        ?: throw NoSuchElementException("No tree URI configured; cannot read note $noteId")

                var doc = noteIdToDoc[noteId]
                if (doc == null) {
                    loadAllNotes()
                    doc = noteIdToDoc[noteId]
                }
                if (doc == null) {
                    throw NoSuchElementException("Note with id '$noteId' not found in tree $treeUri")
                }

                val rawText = fileSource.readText(doc)
                val lastModifiedMs = doc.lastModified()
                val modifiedInstant =
                    if (lastModifiedMs > 0) {
                        Instant.ofEpochMilli(lastModifiedMs)
                    } else {
                        Instant.now()
                    }
                val fallback =
                    FileFallbackMetadata(
                        fileCreated = modifiedInstant,
                        fileModified = modifiedInstant,
                        appVersion = "Locus 1.0.0",
                    )
                val parsed = parser.parse(rawText, fallback)
                parsed.body
            }

        override suspend fun listFolders(): List<String> =
            withContext(Dispatchers.IO) {
                val treeUri = getEffectiveTreeUri() ?: return@withContext emptyList()
                fileSource.listFolders(treeUri)
            }

        private suspend fun loadAllNotes(): List<Note> =
            withContext(Dispatchers.IO) {
                val treeUri = getEffectiveTreeUri() ?: return@withContext emptyList()
                val files = fileSource.listMarkdownFiles(treeUri)
                val root = fileSource.getRootDocument(treeUri)
                val notes = mutableListOf<Note>()

                for (file in files) {
                    val rawText = runCatching { fileSource.readText(file) }.getOrNull() ?: continue
                    val lastModifiedMs = file.lastModified()
                    val modifiedInstant =
                        if (lastModifiedMs > 0) {
                            Instant.ofEpochMilli(lastModifiedMs)
                        } else {
                            Instant.now()
                        }
                    val fallback =
                        FileFallbackMetadata(
                            fileCreated = modifiedInstant,
                            fileModified = modifiedInstant,
                            appVersion = "Locus 1.0.0",
                        )
                    val parsed = parser.parse(rawText, fallback)
                    val folderPath = computeFolderPath(file, root)
                    val note = parsed.toDomain(folderPath)
                    noteIdToDoc[note.id] = file
                    notes.add(note)
                }
                notes
            }

        internal fun computeFolderPath(
            doc: DocumentFile,
            root: DocumentFile?,
        ): String {
            if (root == null) return ""
            val segments = mutableListOf<String>()
            var current = doc.parentFile
            while (current != null && current.uri != root.uri && current != root) {
                val name = current.name
                if (!name.isNullOrBlank()) {
                    segments.add(0, name)
                }
                current = current.parentFile
            }
            return segments.joinToString("/")
        }

        private fun normalizeFolderPath(path: String): String = path.trim().trim('/')
    }
