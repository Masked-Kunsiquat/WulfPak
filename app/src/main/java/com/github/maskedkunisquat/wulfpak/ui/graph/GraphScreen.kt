package com.github.maskedkunisquat.wulfpak.ui.graph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.maskedkunisquat.wulfpak.core.data.entity.RelCategory
import com.github.maskedkunisquat.wulfpak.core.logic.graph.GraphLayoutEngine
import com.github.maskedkunisquat.wulfpak.core.logic.graph.GraphNode
import com.github.maskedkunisquat.wulfpak.ui.theme.WulfPakTheme
import java.util.UUID

@Composable
fun GraphScreen(
    onNavigateToPerson: (UUID) -> Unit,
    viewModel: GraphViewModel = viewModel(),
) {
    val nodes by viewModel.nodes.collectAsStateWithLifecycle()
    val positions by viewModel.positions.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val meId by viewModel.meId.collectAsStateWithLifecycle()

    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        GraphCanvas(
            nodes = nodes,
            positions = positions,
            meId = meId,
            onNodeTap = onNavigateToPerson,
        )
    }
}

@Composable
fun GraphCanvas(
    nodes: List<GraphNode>,
    positions: Map<UUID, Offset>,
    meId: UUID?,
    onNodeTap: (UUID) -> Unit,
    modifier: Modifier = Modifier,
) {
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }

    val density = LocalDensity.current
    val baseRadius  = with(density) { 8.dp.toPx() }
    val ringStroke  = with(density) { 1.dp.toPx() }

    val familyColor = MaterialTheme.colorScheme.tertiary
    val friendColor = MaterialTheme.colorScheme.primary
    val workColor   = MaterialTheme.colorScheme.secondary
    val otherColor  = MaterialTheme.colorScheme.outline
    val meColor     = MaterialTheme.colorScheme.errorContainer
    val ringColor   = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)

    val labelPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 28f
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
    }

    val rBase = minOf(GraphLayoutEngine.LAYOUT_WIDTH, GraphLayoutEngine.LAYOUT_HEIGHT) / 2f
    val layoutCenter = positions[meId]
        ?: Offset(GraphLayoutEngine.LAYOUT_WIDTH / 2f, GraphLayoutEngine.LAYOUT_HEIGHT / 2f)

    Box(modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(0.3f, 3f)
                        val effectiveZoom = newScale / scale
                        panOffset = centroid + (panOffset - centroid) * effectiveZoom + pan
                        scale = newScale
                    }
                }
                .pointerInput(positions) {
                    detectTapGestures { tapPos ->
                        val canvasPos = (tapPos - panOffset) / scale
                        val tapped = nodes.minByOrNull { node ->
                            val nodePos = positions[node.id] ?: return@minByOrNull Float.MAX_VALUE
                            (canvasPos - nodePos).getDistance()
                        }
                        if (tapped != null) {
                            val nodePos = positions[tapped.id]
                            if (nodePos != null) {
                                val nodeRadius = baseRadius * (if (tapped.id == meId) 2f else 1f + (tapped.closenessScore ?: 0.3f) * 0.5f)
                                if ((canvasPos - nodePos).getDistance() <= nodeRadius * 2) {
                                    onNodeTap(tapped.id)
                                }
                            }
                        }
                    }
                }
        ) {
            withTransform({
                translate(panOffset.x, panOffset.y)
                scale(scale, scale, Offset.Zero)
            }) {
                // 1. Ring outlines (AirDrop-style concentric circles)
                for (frac in listOf(GraphLayoutEngine.INNER_FRAC, GraphLayoutEngine.MIDDLE_FRAC, GraphLayoutEngine.OUTER_FRAC)) {
                    drawCircle(
                        color = ringColor,
                        radius = rBase * frac,
                        center = layoutCenter,
                        style = Stroke(width = ringStroke),
                    )
                }

                // 2. Node circles
                for (node in nodes) {
                    val pos = positions[node.id] ?: continue
                    val isMe = node.id == meId
                    val nodeColor = if (isMe) meColor else when (node.category) {
                        RelCategory.FAMILY -> familyColor
                        RelCategory.FRIEND -> friendColor
                        RelCategory.WORK   -> workColor
                        RelCategory.OTHER  -> otherColor
                    }
                    val nodeRadius = baseRadius * (if (isMe) 2f else 1f + (node.closenessScore ?: 0.3f) * 0.5f)
                    drawCircle(color = nodeColor, radius = nodeRadius, center = pos)
                }

                // 3. Name labels — skip at low zoom to avoid overlap
                if (scale > 0.5f) {
                    for (node in nodes) {
                        val pos = positions[node.id] ?: continue
                        val isMe = node.id == meId
                        val nodeRadius = baseRadius * (if (isMe) 2f else 1f + (node.closenessScore ?: 0.3f) * 0.5f)
                        drawContext.canvas.nativeCanvas.drawText(
                            if (isMe) "You" else node.name,
                            pos.x,
                            pos.y + nodeRadius + labelPaint.textSize,
                            labelPaint,
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 700)
@Composable
fun GraphCanvasPreview() {
    val meId = UUID.randomUUID()
    val ids  = List(7) { UUID.randomUUID() }
    val nodes = listOf(
        GraphNode(meId,   "You",     RelCategory.OTHER,  null),
        GraphNode(ids[0], "Alice",   RelCategory.FRIEND, 0.9f),
        GraphNode(ids[1], "Frank",   RelCategory.FAMILY, 0.85f),
        GraphNode(ids[2], "Bob",     RelCategory.FRIEND, 0.5f),
        GraphNode(ids[3], "Dave",    RelCategory.WORK,   0.45f),
        GraphNode(ids[4], "Heather", RelCategory.OTHER,  0.15f),
        GraphNode(ids[5], "Carol",   RelCategory.FRIEND, 0.72f),
        GraphNode(ids[6], "Eve",     RelCategory.WORK,   0.1f),
    )
    val positions = GraphLayoutEngine.layout(nodes = nodes, meId = meId, width = 380f, height = 700f)
    WulfPakTheme {
        GraphCanvas(nodes = nodes, positions = positions, meId = meId, onNodeTap = {})
    }
}
