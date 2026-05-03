package com.github.maskedkunisquat.wulfpak.core.logic.graph

import androidx.compose.ui.geometry.Offset
import java.util.UUID
import kotlin.math.sqrt
import kotlin.random.Random

object GraphLayoutEngine {

    private const val ITERATIONS = 300
    private const val PADDING = 40f
    private const val COOLING = 0.95f

    fun layout(
        nodes: List<GraphNode>,
        edges: List<GraphEdge>,
        width: Float,
        height: Float,
    ): Map<UUID, Offset> {
        if (nodes.isEmpty()) return emptyMap()

        val k = sqrt(width * height / nodes.size)
        val rng = Random(seed = 42)

        val px = FloatArray(nodes.size) { rng.nextFloat() * (width - 2 * PADDING) + PADDING }
        val py = FloatArray(nodes.size) { rng.nextFloat() * (height - 2 * PADDING) + PADDING }
        val idx = nodes.mapIndexed { i, n -> n.id to i }.toMap()

        val dx = FloatArray(nodes.size)
        val dy = FloatArray(nodes.size)

        var temperature = width / 10f

        repeat(ITERATIONS) {
            dx.fill(0f); dy.fill(0f)

            // Repulsive forces — all pairs
            for (i in nodes.indices) {
                for (j in nodes.indices) {
                    if (i == j) continue
                    val diffX = px[i] - px[j]
                    val diffY = py[i] - py[j]
                    val dist = sqrt(diffX * diffX + diffY * diffY).coerceAtLeast(0.01f)
                    val f = k * k / dist
                    dx[i] += diffX / dist * f
                    dy[i] += diffY / dist * f
                }
            }

            // Attractive forces — edges only, weighted by closenessScore
            for (edge in edges) {
                val i = idx[edge.fromId] ?: continue
                val j = idx[edge.toId] ?: continue
                val diffX = px[i] - px[j]
                val diffY = py[i] - py[j]
                val dist = sqrt(diffX * diffX + diffY * diffY).coerceAtLeast(0.01f)
                val f = dist * dist / k * edge.closenessScore
                dx[i] -= diffX / dist * f
                dy[i] -= diffY / dist * f
                dx[j] += diffX / dist * f
                dy[j] += diffY / dist * f
            }

            // Apply displacements capped at temperature, then clamp to bounds
            for (i in nodes.indices) {
                val len = sqrt(dx[i] * dx[i] + dy[i] * dy[i]).coerceAtLeast(0.01f)
                val cap = len.coerceAtMost(temperature)
                px[i] = (px[i] + dx[i] / len * cap).coerceIn(PADDING, width - PADDING)
                py[i] = (py[i] + dy[i] / len * cap).coerceIn(PADDING, height - PADDING)
            }

            temperature *= COOLING
        }

        return nodes.mapIndexed { i, n -> n.id to Offset(px[i], py[i]) }.toMap()
    }
}
