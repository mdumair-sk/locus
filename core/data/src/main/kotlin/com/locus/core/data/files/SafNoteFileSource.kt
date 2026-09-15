package com.locus.core.data.files

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

interface SafNoteFileSource {
    fun listMarkdownFiles(treeUri: Uri): List<DocumentFile>

    fun readText(doc: DocumentFile): String

    fun listFolders(treeUri: Uri): List<String>

    fun getRootDocument(treeUri: Uri): DocumentFile?
}

@Singleton
class AndroidSafNoteFileSource
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : SafNoteFileSource {
        override fun getRootDocument(treeUri: Uri): DocumentFile? = DocumentFile.fromTreeUri(context, treeUri)

        override fun listMarkdownFiles(treeUri: Uri): List<DocumentFile> {
            val root = getRootDocument(treeUri) ?: return emptyList()
            return collectMarkdownFiles(root)
        }

        private fun collectMarkdownFiles(dir: DocumentFile): List<DocumentFile> {
            val files = mutableListOf<DocumentFile>()
            val children = runCatching { dir.listFiles() }.getOrNull().orEmpty()
            for (child in children) {
                val name = child.name ?: ""
                when {
                    child.isDirectory && !name.startsWith(".") -> files.addAll(collectMarkdownFiles(child))
                    child.isFile && name.endsWith(".md", ignoreCase = true) -> files.add(child)
                }
            }
            return files
        }

        override fun readText(doc: DocumentFile): String =
            context.contentResolver.openInputStream(doc.uri)?.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            } ?: throw IOException("Could not open input stream for ${doc.uri}")

        override fun listFolders(treeUri: Uri): List<String> {
            val root = getRootDocument(treeUri) ?: return emptyList()
            val folders = mutableListOf<String>()
            collectFolders(root, "", folders)
            return folders.sorted()
        }

        private fun collectFolders(
            dir: DocumentFile,
            currentPath: String,
            out: MutableList<String>,
        ) {
            val children = runCatching { dir.listFiles() }.getOrNull().orEmpty()
            for (child in children) {
                val name = child.name ?: ""
                if (child.isDirectory && !name.startsWith(".")) {
                    val childPath = if (currentPath.isEmpty()) name else "$currentPath/$name"
                    out.add(childPath)
                    collectFolders(child, childPath, out)
                }
            }
        }
    }
