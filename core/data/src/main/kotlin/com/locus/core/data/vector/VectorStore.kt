package com.locus.core.data.vector

import com.locus.core.domain.search.ChunkMetadata
import com.locus.core.domain.search.ChunkRepository
import com.locus.core.domain.search.EmbeddedChunk
import com.locus.core.domain.search.RankedChunk
import org.json.JSONArray
import java.util.PriorityQueue
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class VectorStore
    @Inject
    constructor(
        private val chunkDao: ChunkDao,
    ) : ChunkRepository {
        companion object {
            private const val MAX_SQL_NOTE_ID_PARAMS = 500
        }

        override suspend fun getMetadata(noteId: String): ChunkMetadata? =
            chunkDao.getMetadata(noteId)?.let {
                ChunkMetadata(
                    sourceChecksum = it.sourceChecksum,
                    embeddingModelId = it.embeddingModelId,
                )
            }

        override suspend fun replaceChunksForNote(
            noteId: String,
            chunks: List<EmbeddedChunk>,
        ) {
            val entities =
                chunks.map { chunk ->
                    ChunkEntity(
                        chunkId = chunk.chunkId,
                        noteId = chunk.noteId,
                        headingPathJson = JSONArray(chunk.headingPath).toString(),
                        text = chunk.text,
                        embedding = chunk.embedding,
                        embeddingModelId = chunk.embeddingModelId,
                        sourceChecksum = chunk.sourceChecksum,
                    )
                }
            chunkDao.replaceChunksForNote(noteId, entities)
        }

        override suspend fun deleteAll() {
            chunkDao.deleteAll()
        }

        override suspend fun search(
            queryVector: FloatArray,
            topK: Int,
            noteIds: Set<String>?,
        ): List<RankedChunk> {
            val candidates = fetchCandidates(noteIds)
            val queryNorm = computeNorm(queryVector)
            if (topK <= 0 || candidates.isEmpty() || queryNorm <= 0f) {
                return emptyList()
            }
            return rankCandidates(candidates, queryVector, queryNorm, topK)
        }

        private fun rankCandidates(
            candidates: List<ChunkEmbeddingTuple>,
            queryVector: FloatArray,
            queryNorm: Float,
            topK: Int,
        ): List<RankedChunk> {
            val pq = PriorityQueue<ScoredChunk>(topK, compareBy { it.score })
            var minScore = Float.NEGATIVE_INFINITY

            for (candidate in candidates) {
                if (candidate.embedding.size != queryVector.size) continue
                val score = computeCosineSimilarity(queryVector, queryNorm, candidate.embedding)
                if (pq.size < topK) {
                    pq.offer(ScoredChunk(candidate.chunkId, candidate.noteId, score))
                    if (pq.size == topK) {
                        minScore = pq.peek()?.score ?: Float.NEGATIVE_INFINITY
                    }
                } else if (score > minScore) {
                    pq.poll()
                    pq.offer(ScoredChunk(candidate.chunkId, candidate.noteId, score))
                    minScore = pq.peek()?.score ?: Float.NEGATIVE_INFINITY
                }
            }

            val sortedList = ArrayList<ScoredChunk>(pq.size)
            while (pq.isNotEmpty()) {
                val item = pq.poll() ?: break
                sortedList.add(item)
            }
            sortedList.reverse()

            return sortedList.mapIndexed { index, item ->
                RankedChunk(
                    chunkId = item.chunkId,
                    noteId = item.noteId,
                    rank = index + 1,
                )
            }
        }

        private suspend fun fetchCandidates(noteIds: Set<String>?): List<ChunkEmbeddingTuple> {
            if (noteIds != null && noteIds.isEmpty()) return emptyList()
            return when {
                noteIds == null -> chunkDao.getAllEmbeddingRows()
                noteIds.size <= MAX_SQL_NOTE_ID_PARAMS ->
                    chunkDao.getEmbeddingRowsForNotes(noteIds.toList())
                else -> chunkDao.getAllEmbeddingRows().filter { it.noteId in noteIds }
            }
        }

        private fun computeNorm(vector: FloatArray): Float {
            var sumSq = 0f
            for (v in vector) {
                sumSq += v * v
            }
            return if (sumSq > 0f) sqrt(sumSq) else 0f
        }

        private fun computeCosineSimilarity(
            query: FloatArray,
            queryNorm: Float,
            candidate: FloatArray,
        ): Float {
            var dot = 0f
            var chunkNormSq = 0f
            for (i in query.indices) {
                val qi = query[i]
                val ci = candidate[i]
                dot += qi * ci
                chunkNormSq += ci * ci
            }
            return if (chunkNormSq > 0f) {
                dot / (queryNorm * sqrt(chunkNormSq))
            } else {
                0f
            }
        }

        private class ScoredChunk(
            val chunkId: String,
            val noteId: String,
            val score: Float,
        )
    }
