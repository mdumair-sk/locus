package com.locus.core.domain.search

data class RankedChunk(
    val chunkId: String,
    val noteId: String,
    val rank: Int,
)

data class FusedResult(
    val chunkId: String,
    val noteId: String,
    val score: Double,
)

/**
 * Reciprocal Rank Fusion (S-1): score = sum over each ranked list the chunk appears in of 1 / (k +
 * rank). A chunk absent from a list contributes nothing for that list. k=60 is RRF's standard
 * smoothing constant.
 */
class ReciprocalRankFusion(
    private val k: Int = 60,
) {
    fun fuse(vararg rankedLists: List<RankedChunk>): List<FusedResult> {
        val scores = linkedMapOf<String, Double>()
        val noteOf = mutableMapOf<String, String>()
        for (list in rankedLists) {
            for (item in list) {
                noteOf[item.chunkId] = item.noteId
                scores[item.chunkId] = (scores[item.chunkId] ?: 0.0) + 1.0 / (k + item.rank)
            }
        }
        return scores.entries.sortedByDescending { it.value }.map {
            FusedResult(it.key, noteOf.getValue(it.key), it.value)
        }
    }
}
