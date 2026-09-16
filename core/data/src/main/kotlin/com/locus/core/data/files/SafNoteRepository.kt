package com.locus.core.data.files

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.locus.core.data.db.NoteDao
import com.locus.core.data.db.NoteIndexEntity
import com.locus.core.domain.notes.Checksum
import com.locus.core.domain.notes.FileFallbackMetadata
import com.locus.core.domain.notes.FlushReceipt
import com.locus.core.domain.notes.FlushTrigger
import com.locus.core.domain.notes.FrontmatterParser
import com.locus.core.domain.notes.IndexUpdateQueue
import com.locus.core.domain.notes.Note
import com.locus.core.domain.notes.NoteFileWriter
import com.locus.core.domain.notes.NoteFlushCoordinator
import com.locus.core.domain.notes.NoteRepository
import com.locus.core.domain.notes.NoteType
import com.locus.core.domain.notes.ParsedNote
import com.locus.core.domain.notes.RescanReport
import com.locus.core.domain.notes.UuidV7
import com.locus.core.domain.notes.toDomain
import com.locus.core.domain.time.Clock
import com.locus.core.domain.time.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
        private val coordinator: NoteFlushCoordinator,
        private val noteDao: NoteDao,
    ) : NoteRepository {
        internal var clock: Clock = Clock { Instant.now() }

        constructor(
            fileSource: SafNoteFileSource,
            parser: FrontmatterParser,
            initialTreeUri: Uri? = null,
            coordinator: NoteFlushCoordinator? = null,
            noteDao: NoteDao? = null,
            clock: Clock = Clock { Instant.now() },
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
            coordinator = coordinator ?: createFallbackCoordinator(),
            noteDao = noteDao ?: createFallbackNoteDao(),
        ) {
            overrideTreeUri = initialTreeUri
            this.clock = clock
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

        override suspend fun createNote(
            folderPath: String,
            title: String,
            type: NoteType,
        ): Note =
            withContext(Dispatchers.IO) {
                val treeUri =
                    getEffectiveTreeUri()
                        ?: error("No tree URI configured; cannot create note")
                val root =
                    fileSource.getRootDocument(treeUri)
                        ?: throw java.io.IOException("Cannot load root document for $treeUri")

                val normalizedFolder = normalizeFolderPath(folderPath)
                val resolvedTitle = resolveUniqueTitle(treeUri, root, normalizedFolder, title)
                val fileName = "$resolvedTitle$MD_EXTENSION"
                val relativePath = if (normalizedFolder.isEmpty()) fileName else "$normalizedFolder/$fileName"

                val id = UuidV7.generate(clock)
                val now = clock.now()
                val initialBody = ""
                val initialChecksum = Checksum.sha256(initialBody)

                val parsedNote =
                    ParsedNote(
                        id = id,
                        title = resolvedTitle,
                        type = type,
                        created = now,
                        modified = now,
                        pinned = false,
                        color = null,
                        tags = emptyList(),
                        history = 0,
                        checksum = initialChecksum,
                        app = "Locus 1.0.0",
                        unknownFields = emptyMap(),
                        body = initialBody,
                        wasRepaired = false,
                        repairNotes = emptyList(),
                    )
                val content = parser.render(parsedNote)

                coordinator.onEdit(id, relativePath, content)
                val flushResult = coordinator.forceFlush(id, FlushTrigger.EDITOR_CLOSE)
                val receipt =
                    flushResult?.getOrThrow()
                        ?: throw java.io.IOException("Failed to flush new note $id ($fileName)")

                val fileChecksum = receipt.checksum
                val entity = parsedNote.toIndexEntity(normalizedFolder, fileChecksum)
                noteDao.upsert(entity)
                val updatedFiles = fileSource.listMarkdownFiles(treeUri)
                val createdDoc =
                    updatedFiles.firstOrNull { doc ->
                        doc.name == fileName &&
                            normalizeFolderPath(computeFolderPath(doc, root)) == normalizedFolder
                    }
                if (createdDoc != null) {
                    noteIdToDoc[id] = createdDoc
                }

                refresh()
                parsedNote.toDomain(normalizedFolder)
            }

        override suspend fun edit(
            noteId: String,
            newBody: String,
        ) {
            withContext(Dispatchers.IO) {
                var doc = noteIdToDoc[noteId]
                if (doc == null) {
                    loadAllNotes()
                    doc = noteIdToDoc[noteId]
                }
                if (doc == null) {
                    throw NoSuchElementException("Note with id '$noteId' not found")
                }

                val rawText = fileSource.readText(doc)
                val parsed = parseDocument(doc, rawText)
                val updatedNote =
                    parsed.copy(
                        body = newBody,
                        modified = clock.now(),
                    )
                val updatedContent = parser.render(updatedNote)
                val path = doc.uri.toString()
                coordinator.onEdit(noteId, path, updatedContent)
            }
        }

        override suspend fun rescan(): RescanReport =
            withContext(Dispatchers.IO) {
                val treeUri = getEffectiveTreeUri() ?: return@withContext RescanReport(0, 0, 0)
                val root = fileSource.getRootDocument(treeUri)
                val files = fileSource.listMarkdownFiles(treeUri)

                val existingEntities = noteDao.observeAll().first()
                val dbEntitiesById = existingEntities.associateBy { it.id }.toMutableMap()

                var added = 0
                var changed = 0
                var removed = 0
                val seenIds = mutableSetOf<String>()

                for (file in files) {
                    val rawText = runCatching { fileSource.readText(file) }.getOrNull() ?: continue
                    val parsed = parseDocument(file, rawText)
                    if (seenIds.add(parsed.id)) {
                        val fileChecksum = Checksum.sha256(rawText)
                        noteIdToDoc[parsed.id] = file
                        val folderPath = computeFolderPath(file, root)
                        val existing = dbEntitiesById[parsed.id]

                        if (existing == null) {
                            added++
                            noteDao.upsert(parsed.toIndexEntity(folderPath, fileChecksum))
                        } else if (existing.checksum != fileChecksum) {
                            changed++
                            noteDao.upsert(parsed.toIndexEntity(folderPath, fileChecksum))
                        } else if (existing.folderPath != folderPath) {
                            noteDao.upsert(existing.copy(folderPath = folderPath))
                        }
                    }
                }

                val missingIds = dbEntitiesById.keys - seenIds
                for (missingId in missingIds) {
                    noteDao.deleteById(missingId)
                    noteIdToDoc.remove(missingId)
                    removed++
                }

                refresh()
                RescanReport(added = added, changed = changed, removed = removed)
            }

        private suspend fun loadAllNotes(): List<Note> =
            withContext(Dispatchers.IO) {
                val treeUri = getEffectiveTreeUri() ?: return@withContext emptyList()
                val files = fileSource.listMarkdownFiles(treeUri)
                val root = fileSource.getRootDocument(treeUri)
                val notes = mutableListOf<Note>()

                for (file in files) {
                    val rawText = runCatching { fileSource.readText(file) }.getOrNull() ?: continue
                    val parsed = parseDocument(file, rawText)
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
        private suspend fun resolveUniqueTitle(
            treeUri: Uri,
            root: DocumentFile,
            normalizedFolder: String,
            desiredTitle: String,
        ): String {
            val folderFiles =
                fileSource.listMarkdownFiles(treeUri).filter {
                    normalizeFolderPath(computeFolderPath(it, root)) == normalizedFolder
                }
            val existingTitles =
                folderFiles
                    .mapNotNull { doc ->
                        doc.name?.let { name ->
                            if (name.endsWith(MD_EXTENSION, ignoreCase = true)) {
                                name.dropLast(MD_EXTENSION_LENGTH)
                            } else {
                                name
                            }
                        }
                    }.toSet()
            return FilenameCollisionResolver.resolve(desiredTitle, existingTitles)
        }

        private fun parseDocument(
            file: DocumentFile,
            rawText: String,
        ): ParsedNote {
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
            return parser.parse(rawText, fallback)
        }

        private fun ParsedNote.toIndexEntity(
            folderPath: String,
            checksum: String,
        ): NoteIndexEntity =
            NoteIndexEntity(
                id = id,
                title = title,
                type = type,
                folderPath = folderPath,
                pinned = pinned,
                color = color,
                tags = tags,
                created = created,
                modified = modified,
                checksum = checksum,
                bodyPreview = body.take(BODY_PREVIEW_LENGTH),
            )

        private companion object {
            private const val MD_EXTENSION = ".md"
            private const val MD_EXTENSION_LENGTH = 3
            private const val BODY_PREVIEW_LENGTH = 200

            fun createFallbackCoordinator(): NoteFlushCoordinator {
                val dummyWriter =
                    object : NoteFileWriter {
                        override suspend fun atomicWrite(
                            noteId: String,
                            path: String,
                            content: String,
                        ): Result<FlushReceipt> =
                            Result.success(
                                FlushReceipt(
                                    noteId = noteId,
                                    checksum = Checksum.sha256(content),
                                    flushedAt = System.currentTimeMillis(),
                                ),
                            )
                    }
                val dummyQueue =
                    object : IndexUpdateQueue {
                        override suspend fun enqueue(receipt: FlushReceipt) = Unit
                    }
                val dummyDispatchers =
                    object : DispatcherProvider {
                        override val io: CoroutineDispatcher = Dispatchers.IO
                        override val default: CoroutineDispatcher = Dispatchers.Default
                        override val main: CoroutineDispatcher = Dispatchers.Main
                        override val mainImmediate: CoroutineDispatcher = Dispatchers.Main.immediate
                    }
                return NoteFlushCoordinator(
                    fileWriter = dummyWriter,
                    indexQueue = dummyQueue,
                    dispatchers = dummyDispatchers,
                    scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
                )
            }

            fun createFallbackNoteDao(): NoteDao =
                object : NoteDao {
                    private val entities = ConcurrentHashMap<String, NoteIndexEntity>()

                    override suspend fun upsert(entity: NoteIndexEntity) {
                        entities[entity.id] = entity
                    }

                    override suspend fun getById(id: String): NoteIndexEntity? = entities[id]

                    override suspend fun deleteById(id: String) {
                        entities.remove(id)
                    }

                    override fun observeAll(): Flow<List<NoteIndexEntity>> = MutableStateFlow(entities.values.toList())

                    override fun observeByFolder(path: String): Flow<List<NoteIndexEntity>> =
                        MutableStateFlow(entities.values.filter { it.folderPath == path })

                    override suspend fun ftsSearch(query: String): List<NoteIndexEntity> = emptyList()
                }
        }
    }
