package com.locus.core.data.chat

import com.locus.core.domain.chat.ActiveModelInfo
import com.locus.core.domain.chat.ModelTier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DefaultActiveModelRepositoryTest {
    private lateinit var repository: DefaultActiveModelRepository

    @Before
    fun setUp() {
        repository = DefaultActiveModelRepository()
    }

    @Test
    fun defaultModelIsCloudGpt4o() =
        runTest {
            val initial = repository.getActiveModel()
            assertEquals("gpt-4o", initial.name)
            assertEquals(ModelTier.CLOUD, initial.tier)
            assertTrue(initial.isCloud)

            val observed = repository.observeActiveModel().first()
            assertEquals(initial, observed)
        }

    @Test
    fun updatingActiveModelEmitsNewValue() =
        runTest {
            val localModel =
                ActiveModelInfo(
                    name = "llama-3.2-3b",
                    tier = ModelTier.LOCAL,
                    contextLength = 8192,
                )
            repository.setActiveModel(localModel)

            assertEquals(localModel, repository.getActiveModel())
            assertTrue(repository.getActiveModel().isLocal)

            val observed = repository.observeActiveModel().first()
            assertEquals(localModel, observed)
        }
}
