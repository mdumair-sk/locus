package com.locus.core.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.locus.core.data.backup.aiDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AgentSettingsStoreTest {
    private lateinit var context: Context
    private lateinit var store: AgentSettingsStore

    @Before
    fun setUp() =
        runTest {
            context = RuntimeEnvironment.getApplication()
            context.aiDataStore.edit { it.clear() }
            store = AgentSettingsStore(context)
        }

    @Test
    fun defaultBulkCap_is50() =
        runTest {
            assertEquals(AgentSettingsStore.DEFAULT_BULK_CAP, store.bulkCap.first())
        }

    @Test
    fun setBulkCap_persistsAndEmits() =
        runTest {
            store.setBulkCap(100)
            assertEquals(100, store.bulkCap.first())

            store.setBulkCap(25)
            assertEquals(25, store.bulkCap.first())
        }

    @Test
    fun setBulkCap_clampsLowerAndUpperBounds() =
        runTest {
            store.setBulkCap(-10)
            assertEquals(AgentSettingsStore.MIN_BULK_CAP, store.bulkCap.first())

            store.setBulkCap(2000)
            assertEquals(AgentSettingsStore.MAX_BULK_CAP, store.bulkCap.first())
        }
}
