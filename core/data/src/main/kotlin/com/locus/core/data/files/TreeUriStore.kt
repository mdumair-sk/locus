package com.locus.core.data.files

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.locus.core.data.backup.storageDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

interface TreeUriStore {
    val treeUriFlow: Flow<Uri?>

    suspend fun getTreeUri(): Uri?

    suspend fun setTreeUri(uri: Uri)
}

@Singleton
class DataStoreTreeUriStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : TreeUriStore {
        private val treeUriKey = stringPreferencesKey("notes_tree_uri")

        override val treeUriFlow: Flow<Uri?> =
            context.storageDataStore.data.map { prefs -> prefs[treeUriKey]?.let { Uri.parse(it) } }

        override suspend fun getTreeUri(): Uri? = treeUriFlow.first()

        override suspend fun setTreeUri(uri: Uri) {
            context.storageDataStore.edit { prefs -> prefs[treeUriKey] = uri.toString() }
        }
    }
