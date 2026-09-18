package com.locus.core.domain.search

interface EmbeddingGateway {
    suspend fun embed(text: String): FloatArray
}
