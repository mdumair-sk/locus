package com.locus.core.ai.embedding

import com.locus.core.ai.llama.LlamaRuntime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class EmbeddingRunner
    @Inject
    constructor(
        private val runtime: LlamaRuntime,
    ) {
        companion object {
            const val TARGET_DIM = 512
        }

        suspend fun embed(text: String): FloatArray {
            val raw = runtime.embed(text).getOrThrow()
            return poolToTargetDimension(raw)
        }

        private fun poolToTargetDimension(raw: FloatArray): FloatArray {
            if (raw.isEmpty()) {
                return FloatArray(TARGET_DIM)
            }
            val pooled =
                if (raw.size == TARGET_DIM) {
                    raw
                } else {
                    val acc = FloatArray(TARGET_DIM)
                    val counts = IntArray(TARGET_DIM)
                    for (i in raw.indices) {
                        val bin = i % TARGET_DIM
                        acc[bin] += raw[i]
                        counts[bin]++
                    }
                    for (i in 0 until TARGET_DIM) {
                        if (counts[i] > 0) {
                            acc[i] /= counts[i].toFloat()
                        }
                    }
                    normalize(acc)
                }
            return pooled
        }

        private fun normalize(vector: FloatArray): FloatArray {
            var sumSq = 0f
            for (v in vector) {
                sumSq += v * v
            }
            val norm = sqrt(sumSq)
            if (norm > 0f) {
                for (i in vector.indices) {
                    vector[i] /= norm
                }
            }
            return vector
        }
    }
