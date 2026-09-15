package com.locus.core.data.files

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.locus.core.domain.notes.Checksum
import com.locus.core.domain.notes.FlushReceipt
import com.locus.core.domain.notes.NoteFileWriter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SafNoteFileWriter
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val fileSource: SafNoteFileSource,
        private val treeUriStore: TreeUriStore,
    ) : NoteFileWriter {
        override suspend fun atomicWrite(
            noteId: String,
            path: String,
            content: String,
        ): Result<FlushReceipt> =
            withContext(Dispatchers.IO) {
                runCatching {
                    when {
                        path.startsWith("content://") -> writeSafDocument(noteId, path, content)
                        isFilesystemPath(path) -> writeFileDirect(noteId, path, content)
                        else -> writeRelativeSafDocument(noteId, path, content)
                    }
                }
            }

        private fun isFilesystemPath(path: String): Boolean =
            path.startsWith("/") ||
                path.startsWith("file://") ||
                (path.length > 2 && path[1] == ':' && (path[2] == '\\' || path[2] == '/'))

        private fun writeFileDirect(
            noteId: String,
            path: String,
            content: String,
        ): FlushReceipt {
            val file = File(if (path.startsWith("file://")) Uri.parse(path).path ?: path else path)
            val parent = file.parentFile ?: File(".")
            if (!parent.exists() && !parent.mkdirs()) {
                throw IOException("Failed to create parent directory for ${file.absolutePath}")
            }
            val tempFile = File(parent, ".${file.name}.${System.currentTimeMillis()}.tmp")
            tempFile.writeText(content, Charsets.UTF_8)
            val renamed = tempFile.renameTo(file)
            if (!renamed) {
                tempFile.copyTo(file, overwrite = true)
                tempFile.delete()
            }
            return FlushReceipt(
                noteId = noteId,
                checksum = Checksum.sha256(content),
                flushedAt = System.currentTimeMillis(),
            )
        }

        private suspend fun writeSafDocument(
            noteId: String,
            path: String,
            content: String,
        ): FlushReceipt {
            val targetUri = Uri.parse(path)
            val treeUri = treeUriStore.getTreeUri()
            val root = treeUri?.let { fileSource.getRootDocument(it) }

            var targetDoc: DocumentFile? = null
            var parentDoc: DocumentFile? = null
            var targetName: String? = null

            if (treeUri != null) {
                val match = fileSource.listMarkdownFiles(treeUri).firstOrNull { it.uri == targetUri }
                if (match != null) {
                    targetDoc = match
                    parentDoc = match.parentFile ?: root
                    targetName = match.name
                }
            }

            if (parentDoc == null) {
                val singleDoc = DocumentFile.fromSingleUri(context, targetUri)
                targetDoc = singleDoc
                targetName = singleDoc?.name
                parentDoc = singleDoc?.parentFile ?: root
            }

            if (parentDoc == null) {
                throw IOException("Cannot resolve parent directory document for $path")
            }

            val finalName = targetName ?: (if (noteId.endsWith(".md")) noteId else "$noteId.md")
            return writeToParentAndReplace(
                noteId = noteId,
                parentDoc = parentDoc,
                targetDoc = targetDoc,
                targetName = finalName,
                content = content,
            )
        }

        private suspend fun writeRelativeSafDocument(
            noteId: String,
            relativePath: String,
            content: String,
        ): FlushReceipt {
            val treeUri =
                treeUriStore.getTreeUri()
                    ?: throw IOException("No tree URI configured for relative path $relativePath")
            val root =
                fileSource.getRootDocument(treeUri)
                    ?: throw IOException("Could not load root document for $treeUri")

            val segments = relativePath.trim('/').split('/')
            val parentDir = resolveSubfolder(root, segments.dropLast(1))
            val fileName = segments.last()
            val targetDoc = parentDir.findFile(fileName)
            return writeToParentAndReplace(
                noteId = noteId,
                parentDoc = parentDir,
                targetDoc = targetDoc,
                targetName = fileName,
                content = content,
            )
        }

        private fun resolveSubfolder(
            root: DocumentFile,
            folderSegments: List<String>,
        ): DocumentFile {
            var currentDir = root
            for (seg in folderSegments) {
                val existing = currentDir.findFile(seg)
                currentDir =
                    if (existing != null && existing.isDirectory) {
                        existing
                    } else {
                        currentDir.createDirectory(seg) ?: currentDir
                    }
            }
            return currentDir
        }

        private fun writeToParentAndReplace(
            noteId: String,
            parentDoc: DocumentFile,
            targetDoc: DocumentFile?,
            targetName: String,
            content: String,
        ): FlushReceipt {
            val tempName = "$targetName.${System.currentTimeMillis()}.tmp"
            val tempDoc =
                parentDoc.createFile("text/markdown", tempName)
                    ?: throw IOException("Failed to create temporary document $tempName")

            var replaced = false
            try {
                writeContent(tempDoc, content)
                replaced = tryRename(tempDoc, targetName)
                if (!replaced) {
                    val target = targetDoc ?: resolveOrCreateTarget(parentDoc, targetName)
                    copyContent(tempDoc, target)
                }
                return FlushReceipt(
                    noteId = noteId,
                    checksum = Checksum.sha256(content),
                    flushedAt = System.currentTimeMillis(),
                )
            } finally {
                if (!replaced) {
                    tempDoc.delete()
                }
            }
        }

        private fun writeContent(
            doc: DocumentFile,
            content: String,
        ) {
            context.contentResolver.openOutputStream(doc.uri, "wt")?.use { stream ->
                stream.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(content)
                    writer.flush()
                }
            } ?: throw IOException("Failed to open output stream for ${doc.uri}")
        }

        private fun tryRename(
            tempDoc: DocumentFile,
            targetName: String,
        ): Boolean =
            try {
                val renamedUri =
                    DocumentsContract.renameDocument(
                        context.contentResolver,
                        tempDoc.uri,
                        targetName,
                    )
                if (renamedUri != null) {
                    val renamedDoc = DocumentFile.fromSingleUri(context, renamedUri)
                    renamedDoc?.name == targetName || renamedDoc == null
                } else {
                    false
                }
            } catch (_: UnsupportedOperationException) {
                false
            } catch (_: IllegalStateException) {
                false
            } catch (_: IOException) {
                false
            } catch (_: SecurityException) {
                false
            }

        private fun resolveOrCreateTarget(
            parentDoc: DocumentFile,
            targetName: String,
        ): DocumentFile =
            parentDoc.findFile(targetName)
                ?: parentDoc.createFile("text/markdown", targetName)
                ?: throw IOException("Failed to create target document $targetName")

        private fun copyContent(
            source: DocumentFile,
            destination: DocumentFile,
        ) {
            context.contentResolver.openInputStream(source.uri)?.use { inStream ->
                context.contentResolver.openOutputStream(destination.uri, "wt")?.use { outStream ->
                    inStream.copyTo(outStream)
                    outStream.flush()
                } ?: throw IOException("Failed to open output stream for ${destination.uri}")
            } ?: throw IOException("Failed to open input stream for ${source.uri}")
        }
    }
