package com.github.maskedkunisquat.wulfpak.ui.graph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.toArgb
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

    var activeCategories by remember { mutableStateOf(RelCategory.entries.toSet()) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        CategoryFilterRow(
            activeCategories = activeCategories,
            onToggle = { cat ->
                activeCategories = if (cat in activeCategories)
                    activeCategories - cat else activeCategories + cat
            },
        )
        if (isLoading) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            GraphCanvas(
                nodes = nodes,
                positions = positions,
                meId = meId,
                activeCategories = activeCategories,
                onNodeTap = onNavigateToPerson,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CategoryFilterRow(
    activeCategories: Set<RelCategory>,
    onToggle: (RelCategory) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RelCategory.entries.forEach { cat ->
            val catColor = when (cat) {
                RelCategory.FAMILY -> MaterialTheme.colorScheme.tertiary
                RelCategory.FRIEND -> MaterialTheme.colorScheme.primary
                RelCategory.WORK   -> MaterialTheme.colorScheme.secondary
                RelCategory.OTHER  -> MaterialTheme.colorScheme.outline
            }
            FilterChip(
                selected = cat in activeCategories,
                onClick = { onToggle(cat) },
                label = { Text(cat.name.lowercase().replaceFirstChar { it.uppercase() }) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = catColor.copy(alpha = 0.20f),
                    selectedLabelColor = catColor,
                ),
            )
        }
    }
}

@Composable
fun GraphCanvas(
    nodes: List<GraphNode>,
    positions: Map<UUID, Offset>,
    meId: UUID?,
    activeCategories: Set<RelCategory>,
    onNodeTap: (UUID) -> Unit,
    modifier: Modifier = Modifier,
) {
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }
    var labelNodeId by remember { mutableStateOf<UUID?>(null) }

    val density = LocalDensity.current
    val baseRadius = with(density) { 8.dp.toPx() }
    val ringStroke = with(density) { 1.dp.toPx() }

    val familyColor = MaterialTheme.colorScheme.tertiary
    val friendColor = MaterialTheme.colorScheme.primary
    val workColor   = MaterialTheme.colorScheme.secondary
    val otherColor  = MaterialTheme.colorScheme.outline
    val meColor     = MaterialTheme.colorScheme.errorContainer
    val ringColor   = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
    val onBackground = MaterialTheme.colorScheme.onBackground

    val labelPaint = remember { android.graphics.Paint().apply {
        textSize = 28f
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
    } }
    labelPaint.color = onBackground.toArgb()

    val rBase = minOf(GraphLayoutEngine.LAYOUT_WIDTH, GraphLayoutEngine.LAYOUT_HEIGHT) / 2f
    val layoutCenter = positions[meId]
        ?: Offset(GraphLayoutEngine.LAYOUT_WIDTH / 2f, GraphLayoutEngine.LAYOUT_HEIGHT / 2f)

    fun nodeRadius(node: GraphNode) =
        baseRadius * (if (node.id == meId) 2f else 1f + (node.closenessScore ?: 0.3f) * 0.5f)

    fun hitTest(canvasPos: Offset): GraphNode? {
        val visible = nodes.filter { it.id == meId || it.category in activeCategories }
        val nearest = visible.minByOrNull { node ->
            val pos = positions[node.id] ?: return@minByOrNull Float.MAX_VALUE
            (canvasPos - pos).getDistance()
        } ?: return null
        val pos = positions[nearest.id] ?: return null
        return if ((canvasPos - pos).getDistance() <= nodeRadius(nearest) * 2) nearest else null
    }

    // 0 = inner, 1 = middle, 2 = outer — determined by distance from layout center
    fun positionRing(pos: Offset): Int {
        val dist = (pos - layoutCenter).getDistance()
        val innerEdge  = rBase * (GraphLayoutEngine.INNER_FRAC  + GraphLayoutEngine.MIDDLE_FRAC) / 2f
        val middleEdge = rBase * (GraphLayoutEngine.MIDDLE_FRAC + GraphLayoutEngine.OUTER_FRAC)  / 2f
        return when {
            dist <= innerEdge  -> 0
            dist <= middleEdge -> 1
            else               -> 2
        }
    }

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
                .pointerInput(positions, activeCategories) {
                    detectTapGestures(
                        onTap = { tapPos ->
                            val hit = hitTest((tapPos - panOffset) / scale)
                            if (hit != null) {
                                labelNodeId = if (labelNodeId == hit.id) null else hit.id
                            } else {
                                labelNodeId = null
                            }
                        },
                        onLongPress = { tapPos ->
                            val hit = hitTest((tapPos - panOffset) / scale)
                            if (hit != null) {
                                labelNodeId = null
                                onNodeTap(hit.id)
                            }
                        },
                    )
                }
        ) {
            withTransform({
                translate(panOffset.x, panOffset.y)
                scale(scale, scale, Offset.Zero)
            }) {
                // 1. Ring outlines
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
                    if (node.id != meId && node.category !in activeCategories) continue
                    val pos = positions[node.id] ?: continue
                    val isMe = node.id == meId
                    val nodeColor = if (isMe) meColor else when (node.category) {
                        RelCategory.FAMILY -> familyColor
                        RelCategory.FRIEND -> friendColor
                        RelCategory.WORK   -> workColor
                        RelCategory.OTHER  -> otherColor
                    }
                    drawCircle(color = nodeColor, radius = nodeRadius(node), center = pos)
                }

                // 3. Labels — zoom-based: inner always, middle at 1.2×, outer at 1.8×
                //    Tap on any node also pins its label regardless of zoom level
                for (node in nodes) {
                    val isMe = node.id == meId
                    if (!isMe && node.category !in activeCategories) continue
                    val pos = positions[node.id] ?: continue
                    val ring = if (isMe) -1 else positionRing(pos)
                    val showLabel = isMe
                        || ring == 0
                        || (ring == 1 && scale >= 1.2f)
                        || (ring == 2 && scale >= 1.8f)
                        || node.id == labelNodeId
                    if (!showLabel) continue
                    drawContext.canvas.nativeCanvas.drawText(
                        if (isMe) "You" else node.name,
                        pos.x,
                        pos.y + nodeRadius(node) + labelPaint.textSize,
                        labelPaint,
                    )
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
        GraphCanvas(
            nodes = nodes,
            positions = positions,
            meId = meId,
            activeCategories = RelCategory.entries.toSet(),
            onNodeTap = {},
        )
    }
}
