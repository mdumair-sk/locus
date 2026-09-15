package com.locus.core.data.search

import com.locus.core.data.db.NoteDao
import com.locus.core.data.db.NoteIndexEntity
import com.locus.core.domain.search.KeywordSearch
import com.locus.core.domain.search.SearchResult
import com.locus.core.domain.search.SearchScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomKeywordSearch @Inject constructor(
    private val noteDao: NoteDao,
) : KeywordSearch {

    override suspend fun search(query: String, scope: SearchScope): List<SearchResult> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val entities = try {
            noteDao.ftsSearch(trimmed)
        } catch (_: Exception) {
            try {
                // Fallback: strip quotes and quote tokens to avoid FTS syntax errors on special characters
                val fallbackQuery = trimmed
                    .replace("\"", "")
                    .split("\\s+".toRegex())
                    .filter { it.isNotBlank() }
                    .joinToString(" ") { "\"$it\"" }
                if (fallbackQuery.isNotBlank()) {
                    noteDao.ftsSearch(fallbackQuery)
                } else {
                    emptyList()
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

        return entities
            .filter { matchesScope(it, scope) }
            .mapIndexed { index, entity ->
                SearchResult(
                    noteId = entity.id,
                    title = entity.title,
                    snippet = entity.bodyPreview,
                    score = 1.0 / (index + 1.0),
                )
            }
    }

    private fun matchesScope(entity: NoteIndexEntity, scope: SearchScope): Boolean {
        if (scope.noteIds.isNotEmpty() && entity.id !in scope.noteIds) {
            return false
        }
        if (scope.folderPaths.isNotEmpty() && !matchesFolder(entity.folderPath, scope.folderPaths)) {
            return false
        }
        if (scope.after != null && entity.modified.isBefore(scope.after)) {
            return false
        }
        if (scope.before != null && entity.modified.isAfter(scope.before)) {
            return false
        }
        return true
    }

    private fun matchesFolder(entityFolder: String, scopedFolders: Set<String>): Boolean {
        val normalizedEntity = normalizeFolder(entityFolder)
        return scopedFolders.any { scoped ->
            val normalizedScoped = normalizeFolder(scoped)
            if (normalizedScoped.isEmpty()) {
                normalizedEntity.isEmpty()
            } else {
                normalizedEntity == normalizedScoped || normalizedEntity.startsWith("$normalizedScoped/")
            }
        }
    }

    private fun normalizeFolder(path: String): String = path.trim().trim('/')
}
