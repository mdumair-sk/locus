package com.locus.core.data.search

import androidx.sqlite.db.SimpleSQLiteQuery
import com.locus.core.data.db.NoteDao
import com.locus.core.domain.search.KeywordSearch
import com.locus.core.domain.search.SearchResult
import com.locus.core.domain.search.SearchScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomKeywordSearch
    @Inject
    constructor(
        private val noteDao: NoteDao,
    ) : KeywordSearch {
        override suspend fun search(
            query: String,
            scope: SearchScope,
        ): List<SearchResult> {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return emptyList()

            val entities =
                try {
                    noteDao.ftsSearchScoped(buildFtsQuery(trimmed, scope))
                } catch (_: Exception) {
                    try {
                        // Fallback: strip quotes and quote tokens to avoid FTS syntax errors on
                        // special characters
                        val fallbackQuery =
                            trimmed
                                .replace("\"", "")
                                .split("\\s+".toRegex())
                                .filter { it.isNotBlank() }
                                .joinToString(" ") { "\"$it\"" }
                        if (fallbackQuery.isNotBlank()) {
                            noteDao.ftsSearchScoped(buildFtsQuery(fallbackQuery, scope))
                        } else {
                            emptyList()
                        }
                    } catch (_: Exception) {
                        emptyList()
                    }
                }

            return entities.mapIndexed { index, entity ->
                SearchResult(
                    noteId = entity.id,
                    title = entity.title,
                    snippet = entity.bodyPreview,
                    score = 1.0 / (index + 1.0),
                )
            }
        }

        private fun buildFtsQuery(
            matchQuery: String,
            scope: SearchScope,
        ): SimpleSQLiteQuery {
            val sql =
                StringBuilder(
                    """
                    SELECT note_index.*
                    FROM note_index
                    JOIN note_fts ON note_index.rowid = note_fts.rowid
                    WHERE note_fts MATCH ?
                    """.trimIndent(),
                )
            val args = mutableListOf<Any>(matchQuery)

            if (scope.folderPaths.isNotEmpty()) {
                val folderClauses = mutableListOf<String>()
                for (folder in scope.folderPaths) {
                    val normalized = normalizeFolder(folder)
                    if (normalized.isEmpty()) {
                        folderClauses.add("note_index.folderPath = ''")
                    } else {
                        folderClauses.add("(note_index.folderPath = ? OR note_index.folderPath LIKE ?)")
                        args.add(normalized)
                        args.add("$normalized/%")
                    }
                }
                if (folderClauses.isNotEmpty()) {
                    sql.append(" AND (").append(folderClauses.joinToString(" OR ")).append(")")
                }
            }

            if (scope.noteIds.isNotEmpty()) {
                val placeholders = scope.noteIds.joinToString(",") { "?" }
                sql.append(" AND note_index.id IN (").append(placeholders).append(")")
                args.addAll(scope.noteIds)
            }

            val after = scope.after
            if (after != null) {
                sql.append(" AND note_index.modified >= ?")
                args.add(after.toEpochMilli())
            }

            val before = scope.before
            if (before != null) {
                sql.append(" AND note_index.modified <= ?")
                args.add(before.toEpochMilli())
            }

            return SimpleSQLiteQuery(sql.toString(), args.toTypedArray())
        }

        private fun normalizeFolder(path: String): String = path.trim().trim('/')
    }
