package com.locus.core.ai.catalog

import kotlinx.serialization.Serializable

@Serializable
data class ModelCatalog(
    val schemaVersion: Int,
    val chat: List<CatalogEntry> = emptyList(),
    val utility: List<CatalogEntry> = emptyList(),
    val embeddings: List<CatalogEntry> = emptyList(),
    val routingDefaults: RoutingDefaults = RoutingDefaults(),
) {
    companion object {
        val EMPTY =
            ModelCatalog(
                schemaVersion = 1,
                chat = emptyList(),
                utility = emptyList(),
                embeddings = emptyList(),
                routingDefaults = RoutingDefaults(),
            )
    }
}

@Serializable
data class CatalogEntry(
    val id: String,
    val name: String = "",
    val repo: String,
    val filename: String,
    val sha256: String,
    val sizeBytes: Long = 0L,
    val contextLength: Int = 4096,
    val description: String = "",
    val supportsTools: Boolean = false,
)

@Serializable
data class RoutingDefaults(
    val chat: String = "",
    val utility: String = "",
    val embeddings: String = "",
)
