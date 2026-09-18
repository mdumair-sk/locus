package com.locus.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.locus.core.data.backup.aiDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import com.locus.core.domain.settings.AgentSettingsStore as DomainAgentSettingsStore

@Singleton
class AgentSettingsStore(
    private val dataStore: DataStore<Preferences>,
) : DomainAgentSettingsStore {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(context.aiDataStore)

    private val bulkCapKey = intPreferencesKey("bulk_operation_cap")

    override val bulkCap: Flow<Int> =
        dataStore.data.map { prefs -> prefs[bulkCapKey] ?: DEFAULT_BULK_CAP }

    override suspend fun setBulkCap(value: Int) {
        val clamped = value.coerceIn(MIN_BULK_CAP, MAX_BULK_CAP)
        dataStore.edit { prefs -> prefs[bulkCapKey] = clamped }
    }

    companion object {
        const val DEFAULT_BULK_CAP = 50
        const val MIN_BULK_CAP = 1
        const val MAX_BULK_CAP = 1000
    }
}
