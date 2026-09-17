package com.locus.core.data.backup

import android.net.Uri
import com.locus.core.domain.backup.ImportExportRepository
import com.locus.core.domain.backup.LibraryImportOutcome
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SafImportExportRepository
    @Inject
    constructor(
        private val backupManager: BackupManager,
        private val libraryImporter: LibraryImporter,
        private val settingsExporter: SettingsExporter,
    ) : ImportExportRepository {
        override suspend fun exportLibrary(destinationUriString: String): Boolean {
            val result = backupManager.runBackup(Uri.parse(destinationUriString))
            if (result is BackupResult.Failure) {
                android.util.Log.e(
                    "SafImportExport",
                    "exportLibrary failed: ${result.cause.message}",
                    result.cause,
                )
            }
            return result is BackupResult.Success
        }

        override suspend fun importLibrary(
            zipUriString: String,
            destinationTreeUriString: String,
        ): LibraryImportOutcome {
            val result =
                libraryImporter.import(
                    Uri.parse(zipUriString),
                    Uri.parse(destinationTreeUriString),
                )
            if (result is ImportResult.Failure) {
                android.util.Log.e(
                    "SafImportExport",
                    "importLibrary failed: ${result.cause.message}",
                    result.cause,
                )
            }
            return when (result) {
                is ImportResult.Success -> LibraryImportOutcome.Success(result.fileCount)
                is ImportResult.InvalidZip -> LibraryImportOutcome.InvalidZip(result.message)
                is ImportResult.Failure ->
                    LibraryImportOutcome.Failure(
                        result.cause.message ?: "Import failed",
                    )
            }
        }

        override suspend fun exportSettings(includeApiKeys: Boolean): String = settingsExporter.export(includeApiKeys)

        override suspend fun importSettings(json: String): Boolean =
            runCatching {
                settingsExporter.import(json)
                true
            }.getOrDefault(false)
    }
