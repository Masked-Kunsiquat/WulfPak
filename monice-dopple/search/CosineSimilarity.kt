package com.yourapp.search

import kotlin.math.sqrt

/**
 * Cosine similarity utilities for 384-dim Snowflake Arctic embeddings.
 *
 * Usage in a SearchRepository:
 *
 *   val queryEmbedding = embeddingProvider.generateEmbedding(queryText)
 *   val results = allItems
 *       .filter { it.embedding.any { v -> v != 0f } }   // skip zero-vectors
 *       .map { it to cosineSimilarity(queryEmbedding, it.embedding) }
 *       .filter { (_, score) -> score.isFinite() && score > 0f }
 *       .sortedByDescending { (_, score) -> score }
 *       .take(limit)
 *       .map { (item, _) -> item }
 *
 * Embed everything that has a text body: Notes, Interactions (note field), Activities.
 * Store embeddings as BLOB (FloatArray via AppTypeConverters) — do NOT use CSV strings.
 * Zero-vector (FloatArray(384)) is the sentinel for "not yet embedded" — filter these out
 * before cosine comparison to avoid spurious matches.
 */
object CosineSimilarity {

    /**
     * Computes cosine similarity between two equal-length float vectors.
     * Returns 0f when either vector is the zero-vector (no valid direction).
     */
    fun compute(a: FloatArray, b: FloatArray): Float {
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        if (normA == 0.0 || normB == 0.0) return 0f
        return (dot / (sqrt(normA) * sqrt(normB))).toFloat()
    }
}
