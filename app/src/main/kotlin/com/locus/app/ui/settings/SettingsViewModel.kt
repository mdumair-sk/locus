package com.locus.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.locus.app.workers.WorkScheduling
import com.locus.core.domain.backup.BackupInterval
import com.locus.core.domain.backup.BackupSettingsRepository
import com.locus.core.domain.notes.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val repo: NoteRepository,
        private val backupSettingsRepo: BackupSettingsRepository,
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        val rootUri: StateFlow<String?> =
            repo
                .observeRootUri()
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = null,
                )

        val backupDestinationUri: StateFlow<String?> =
            backupSettingsRepo
                .observeBackupDestinationUri()
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = null,
                )

        val backupInterval: StateFlow<BackupInterval> =
            backupSettingsRepo
                .observeBackupInterval()
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = BackupInterval.WEEKLY,
                )

        val lastBackupTime: StateFlow<Long?> =
            backupSettingsRepo
                .observeLastBackupTime()
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = null,
                )

        val isBackingUp: StateFlow<Boolean> =
            WorkManager
                .getInstance(context)
                .getWorkInfosForUniqueWorkFlow(WorkScheduling.ON_DEMAND_BACKUP_WORK_NAME)
                .map { workInfos ->
                    workInfos.any {
                        it.state == WorkInfo.State.RUNNING ||
                            it.state == WorkInfo.State.ENQUEUED
                    }
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = false,
                )

        fun setRootFolder(uriString: String) {
            viewModelScope.launch { repo.setRootUri(uriString) }
        }

        fun setBackupDestination(uriString: String) {
            viewModelScope.launch { backupSettingsRepo.setBackupDestinationUri(uriString) }
        }

        fun setBackupInterval(interval: BackupInterval) {
            viewModelScope.launch {
                backupSettingsRepo.setBackupInterval(interval)
                WorkScheduling.schedulePeriodicBackup(context, interval)
            }
        }

        fun backupNow() {
            WorkScheduling.triggerOnDemandBackup(context)
        }

        private companion object {
            private const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
