package com.locus.core.domain.backup

import kotlinx.coroutines.flow.Flow

private const val DAYS_DAILY = 1L
private const val DAYS_WEEKLY = 7L
private const val DAYS_MONTHLY = 30L

enum class BackupInterval(
    val days: Long,
) {
    DAILY(DAYS_DAILY),
    WEEKLY(DAYS_WEEKLY),
    MONTHLY(DAYS_MONTHLY),
    OFF(0L),
}

interface BackupSettingsRepository {
    fun observeBackupDestinationUri(): Flow<String?>

    suspend fun getBackupDestinationUri(): String?

    suspend fun setBackupDestinationUri(uriString: String)

    fun observeBackupInterval(): Flow<BackupInterval>

    suspend fun getBackupInterval(): BackupInterval

    suspend fun setBackupInterval(interval: BackupInterval)

    fun observeLastBackupTime(): Flow<Long?>

    suspend fun setLastBackupTime(timestamp: Long)
}
