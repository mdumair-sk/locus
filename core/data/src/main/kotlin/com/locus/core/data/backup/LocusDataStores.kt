package com.locus.core.data.backup

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

internal val Context.storageDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "locus_storage_preferences")
internal val Context.backupDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "locus_backup_preferences")
internal val Context.aiDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "locus_ai_preferences")
