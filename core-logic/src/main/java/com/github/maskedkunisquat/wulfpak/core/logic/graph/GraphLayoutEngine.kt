package com.github.maskedkunisquat.wulfpak.core.logic.graph

import androidx.compose.ui.geometry.Offset
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object GraphLayoutEngine {

    const val LAYOUT_WIDTH  = 1080f
    const val LAYOUT_HEIGHT = 1920f
    const val INNER_FRAC    = 0.22f
    const val MIDDLE_FRAC   = 0.42f
    const val OUTER_FRAC    = 0.62f

    private const val INNER_MULTIPLIER  = 0.50f
    private const val MIDDLE_MULTIPLIER = 0.08f
    private const val INNER_FLOOR       = 0.015f
    private const val MIDDLE_FLOOR      = 0.001f

    fun layout(
        nodes: List<GraphNode>,
        meId: UUID?,
        width: Float = LAYOUT_WIDTH,
        height: Float = LAYOUT_HEIGHT,
    ): Map<UUID, Offset> {
        if (nodes.isEmpty()) return emptyMap()

        val cx    = width / 2f
        val cy    = height / 2f
        val rBase = minOf(width, height) / 2f
        val out   = mutableMapOf<UUID, Offset>()

        if (meId != null) out[meId] = Offset(cx, cy)

        val others = nodes.filter { it.id != meId }

        val maxScore = others.mapNotNull { it.closenessScore }.maxOrNull() ?: 1f
        val innerThreshold  = (maxScore * INNER_MULTIPLIER).coerceAtLeast(INNER_FLOOR)
        val middleThreshold = (maxScore * MIDDLE_MULTIPLIER).coerceAtLeast(MIDDLE_FLOOR)

        val inner  = others.filter { (it.closenessScore ?: 0f) >= innerThreshold }
        val middle = others.filter {
            val s = it.closenessScore ?: 0f
            s >= middleThreshold && s < innerThreshold
        }
        val outer  = others.filter { (it.closenessScore ?: 0f) < middleThreshold }

        placeOnRing(inner,  cx, cy, rBase * INNER_FRAC,  out)
        placeOnRing(middle, cx, cy, rBase * MIDDLE_FRAC, out)
        placeOnRing(outer,  cx, cy, rBase * OUTER_FRAC,  out)

        return out
    }

    private fun placeOnRing(
        nodes: List<GraphNode>,
        cx: Float,
        cy: Float,
        radius: Float,
        out: MutableMap<UUID, Offset>,
    ) {
        if (nodes.isEmpty()) return
        val step  = (2.0 * PI / nodes.size).toFloat()
        val start = (-PI / 2.0).toFloat()
        nodes.forEachIndexed { i, node ->
            val angle = start + i * step
            out[node.id] = Offset(cx + cos(angle) * radius, cy + sin(angle) * radius)
        }
    }
}
