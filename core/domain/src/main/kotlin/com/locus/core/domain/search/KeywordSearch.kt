package com.locus.core.domain.search

import java.time.Instant

data class SearchScope(
    val folderPaths: Set<String> = emptySet(),
    val noteIds: Set<String> = emptySet(),
    val after: Instant? = null,
    val before: Instant? = null,
) {
    fun isUnconstrained(): Boolean = folderPaths.isEmpty() && noteIds.isEmpty() && after == null && before == null
}

interface KeywordSearch {
    suspend fun search(
        query: String,
        scope: SearchScope = SearchScope(),
    ): List<SearchResult>
}
