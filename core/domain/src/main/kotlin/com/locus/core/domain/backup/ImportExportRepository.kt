package com.locus.core.domain.backup

sealed interface LibraryImportOutcome {
    data class Success(
        val fileCount: Int,
    ) : LibraryImportOutcome

    data class InvalidZip(
        val message: String,
    ) : LibraryImportOutcome

    data class Failure(
        val message: String,
    ) : LibraryImportOutcome
}

interface ImportExportRepository {
    suspend fun exportLibrary(destinationUriString: String): Boolean

    suspend fun importLibrary(
        zipUriString: String,
        destinationTreeUriString: String,
    ): LibraryImportOutcome

    suspend fun exportSettings(includeApiKeys: Boolean): String

    suspend fun importSettings(json: String): Boolean
}
