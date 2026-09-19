package com.locus.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.locus.core.data.backup.aiDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import com.locus.core.domain.settings.DismissedRecommendationsStore as DomainDismissedRecommendationsStore

@Singleton
class DismissedRecommendationsStore(
    private val dataStore: DataStore<Preferences>,
) : DomainDismissedRecommendationsStore {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(context.aiDataStore)

    private val dismissedKey = stringSetPreferencesKey("dismissed_recommendations")

    override val dismissedIds: Flow<Set<String>> =
        dataStore.data.map { prefs -> prefs[dismissedKey] ?: emptySet() }

    override suspend fun dismiss(entryId: String) {
        dataStore.edit { prefs ->
            val current = prefs[dismissedKey] ?: emptySet()
            prefs[dismissedKey] = current + entryId
        }
    }
}
