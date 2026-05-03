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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.maskedkunisquat.wulfpak.core.data.entity.RelCategory
import com.github.maskedkunisquat.wulfpak.core.logic.graph.GraphEdge
import com.github.maskedkunisquat.wulfpak.core.logic.graph.GraphNode
import com.github.maskedkunisquat.wulfpak.ui.theme.WulfPakTheme
import java.util.UUID

@Composable
fun GraphScreen(
    onNavigateToPerson: (UUID) -> Unit,
    viewModel: GraphViewModel = viewModel(),
) {
    val nodes by viewModel.nodes.collectAsStateWithLifecycle()
    val edges by viewModel.edges.collectAsStateWithLifecycle()
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
            edges = edges,
            positions = positions,
            meId = meId,
            onNodeTap = onNavigateToPerson,
        )
    }
}

@Composable
fun GraphCanvas(
    nodes: List<GraphNode>,
    edges: List<GraphEdge>,
    positions: Map<UUID, Offset>,
    meId: UUID?,
    onNodeTap: (UUID) -> Unit,
    modifier: Modifier = Modifier,
) {
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }

    val density = LocalDensity.current
    val baseRadius = with(density) { 8.dp.toPx() }
    val strokeWidth = with(density) { 1.5f.dp.toPx() }

    val familyColor = MaterialTheme.colorScheme.tertiary
    val friendColor = MaterialTheme.colorScheme.primary
    val workColor = MaterialTheme.colorScheme.secondary
    val otherColor = MaterialTheme.colorScheme.outline
    val meColor = MaterialTheme.colorScheme.errorContainer

    val labelPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 28f
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
    }

    Box(modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        panOffset += pan
                        scale = (scale * zoom).coerceIn(0.3f, 3f)
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
                                val nodeRadius = baseRadius * (1f + (tapped.closenessScore ?: 0.3f) * 0.5f)
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
                // 1. Edges
                for (edge in edges) {
                    val from = positions[edge.fromId] ?: continue
                    val to = positions[edge.toId] ?: continue
                    drawLine(
                        color = otherColor.copy(alpha = 0.4f),
                        start = from,
                        end = to,
                        strokeWidth = strokeWidth,
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
                        val label = if (isMe) "You" else node.name
                        drawContext.canvas.nativeCanvas.drawText(
                            label,
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
    val ids = List(8) { UUID.randomUUID() }
    val nodes = listOf(
        GraphNode(ids[0], "Alice",   RelCategory.FRIEND, 0.9f),
        GraphNode(ids[1], "Bob",     RelCategory.FRIEND, 0.7f),
        GraphNode(ids[2], "Carol",   RelCategory.FRIEND, 0.5f),
        GraphNode(ids[3], "Dave",    RelCategory.WORK,   0.4f),
        GraphNode(ids[4], "Eve",     RelCategory.WORK,   0.6f),
        GraphNode(ids[5], "Frank",   RelCategory.FAMILY, 0.95f),
        GraphNode(ids[6], "Grace",   RelCategory.FAMILY, 0.8f),
        GraphNode(ids[7], "Heather", RelCategory.OTHER,  0.2f),
    )
    val edges = listOf(
        GraphEdge(ids[0], ids[1], RelCategory.FRIEND, 0.8f),
        GraphEdge(ids[0], ids[2], RelCategory.FRIEND, 0.5f),
        GraphEdge(ids[1], ids[2], RelCategory.FRIEND, 0.6f),
        GraphEdge(ids[3], ids[4], RelCategory.WORK,   0.4f),
        GraphEdge(ids[5], ids[6], RelCategory.FAMILY, 0.9f),
        GraphEdge(ids[0], ids[7], RelCategory.OTHER,  0.2f),
    )
    val positions = mapOf(
        ids[0] to Offset(190f, 200f), ids[1] to Offset(120f, 300f),
        ids[2] to Offset(260f, 320f), ids[3] to Offset(80f,  500f),
        ids[4] to Offset(160f, 520f), ids[5] to Offset(310f, 180f),
        ids[6] to Offset(330f, 280f), ids[7] to Offset(230f, 460f),
    )
    WulfPakTheme {
        GraphCanvas(nodes = nodes, edges = edges, positions = positions, meId = ids[0], onNodeTap = {})
    }
}
