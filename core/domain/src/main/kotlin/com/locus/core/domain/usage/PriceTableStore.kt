package com.locus.core.domain.usage

import com.locus.core.domain.providers.ProviderCapabilities
import kotlinx.coroutines.flow.Flow

/** Price per million input and output tokens for a provider. */
data class ProviderPrice(
    val inputPricePerMillion: Double,
    val outputPricePerMillion: Double,
)

/** P-5: Editable price-per-million-token table seeded from capabilities and user-overridable. */
interface PriceTableStore {
    val prices: Flow<Map<String, ProviderPrice>>

    suspend fun getPrice(providerId: String): ProviderPrice

    suspend fun setPrice(
        providerId: String,
        price: ProviderPrice,
    )

    suspend fun resetPrice(providerId: String)

    suspend fun seedFromCapabilities(
        providerId: String,
        capabilities: ProviderCapabilities,
    )
}
