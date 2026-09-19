package com.locus.core.domain.models

import com.locus.core.domain.chat.ActiveModelInfo
import com.locus.core.domain.providers.ProviderCapabilities
import com.locus.core.domain.routing.ModelRef

data class RegistryEntry(
    val ref: ModelRef,
    val contextLength: Int,
    val capabilities: ProviderCapabilities?,
    val isOffline: Boolean,
    val benchmarkedTokPerSecond: Double?,
) {
    fun toActiveModelInfo(): ActiveModelInfo =
        ActiveModelInfo(
            name = ref.id,
            tier = ref.tier,
            contextLength = contextLength,
        )
}
