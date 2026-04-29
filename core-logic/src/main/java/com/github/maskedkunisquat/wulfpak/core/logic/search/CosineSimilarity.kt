package com.github.maskedkunisquat.wulfpak.core.logic.search

import kotlin.math.sqrt

object CosineSimilarity {

    /**
     * Cosine similarity between two equal-length float vectors.
     * Returns 0f when either vector is the zero-vector.
     */
    fun compute(a: FloatArray, b: FloatArray): Float {
        var dot   = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            dot   += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        if (normA == 0.0 || normB == 0.0) return 0f
        return (dot / (sqrt(normA) * sqrt(normB))).toFloat()
    }
}
