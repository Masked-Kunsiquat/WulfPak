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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.imageLoader
import coil.request.ImageRequest
import com.github.maskedkunisquat.wulfpak.core.data.entity.RelCategory
import com.github.maskedkunisquat.wulfpak.core.logic.graph.GraphLayoutEngine
import com.github.maskedkunisquat.wulfpak.core.logic.graph.GraphNode
import java.io.File
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

    var activeCategories  by remember { mutableStateOf(RelCategory.entries.toSet()) }
    var animationEnabled  by remember { mutableStateOf(true) }

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
            Box(modifier = Modifier.weight(1f)) {
                GraphCanvas(
                    nodes            = nodes,
                    positions        = positions,
                    meId             = meId,
                    activeCategories = activeCategories,
                    animationEnabled = animationEnabled,
                    onNodeTap        = onNavigateToPerson,
                    modifier         = Modifier.fillMaxSize(),
                )
                SmallFloatingActionButton(
                    onClick = { animationEnabled = !animationEnabled },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Icon(
                        imageVector = if (animationEnabled) Icons.Default.Pause
                                      else Icons.Default.PlayArrow,
                        contentDescription = if (animationEnabled) "Pause animation"
                                             else "Resume animation",
                    )
                }
            }
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
    animationEnabled: Boolean,
    onNodeTap: (UUID) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }
    var labelNodeId by remember { mutableStateOf<UUID?>(null) }

    // Bitmaps loaded once per node; keyed by UUID so only new nodes trigger loads
    val bitmaps = remember { mutableStateMapOf<UUID, android.graphics.Bitmap>() }
    LaunchedEffect(nodes) {
        nodes.forEach { node ->
            if (node.photoUri == null || bitmaps.containsKey(node.id)) return@forEach
            val file = File(node.photoUri)
            if (!file.exists()) return@forEach
            val result = context.imageLoader.execute(
                ImageRequest.Builder(context)
                    .data(file)
                    .size(256, 256)
                    .allowHardware(false)
                    .build()
            )
            val bmp = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
            if (bmp != null) bitmaps[node.id] = bmp
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "nodeFloat")
    val floatProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue  = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "floatProgress",
    )

    val density = LocalDensity.current
    val baseRadius     = with(density) { 8.dp.toPx() }
    val ringStroke     = with(density) { 1.dp.toPx() }
    val borderPx       = with(density) { 2.5.dp.toPx() }
    val floatAmplitude = baseRadius * 0.6f

    val familyColor   = MaterialTheme.colorScheme.tertiary
    val friendColor   = MaterialTheme.colorScheme.primary
    val workColor     = MaterialTheme.colorScheme.secondary
    val otherColor    = MaterialTheme.colorScheme.outline
    val meColor       = MaterialTheme.colorScheme.errorContainer
    val ringColor     = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
    val onBackground  = MaterialTheme.colorScheme.onBackground
    val onFamilyColor = MaterialTheme.colorScheme.onTertiary
    val onFriendColor = MaterialTheme.colorScheme.onPrimary
    val onWorkColor   = MaterialTheme.colorScheme.onSecondary
    val onOtherColor  = MaterialTheme.colorScheme.onBackground
    val onMeColor     = MaterialTheme.colorScheme.onErrorContainer

    val labelPaint = remember { android.graphics.Paint().apply {
        textSize = 28f
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
    } }
    labelPaint.color = onBackground.toArgb()

    val initialsPaint = remember { android.graphics.Paint().apply {
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
        isFakeBoldText = true
    } }
    val photoPaint = remember { android.graphics.Paint().apply { isAntiAlias = true } }

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
        // Accessible companion: hidden from sighted users, enumerable by TalkBack/keyboard
        val accessibleNodes = nodes.filter { it.id == meId || it.category in activeCategories }
        accessibleNodes.forEach { node ->
            Box(
                Modifier.semantics(mergeDescendants = true) {
                    contentDescription = if (node.id == meId) "You"
                        else "${node.name}, ${node.category.name.lowercase()}"
                    role = Role.Button
                    onClick(label = "Open") { onNodeTap(node.id); true }
                }
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clearAndSetSemantics { contentDescription = "Social network graph" }
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

                // 2. Node circles — photo with colored border, or category fill with initials
                nodes.forEachIndexed { idx, node ->
                    if (node.id != meId && node.category !in activeCategories) return@forEachIndexed
                    val pos = positions[node.id] ?: return@forEachIndexed
                    val phase = idx * 1.1f
                    val drawPos = if (!animationEnabled || node.id == meId) pos else pos + Offset(
                        sin(floatProgress + phase) * floatAmplitude,
                        cos(floatProgress * 0.73f + phase) * floatAmplitude,
                    )
                    val isMe = node.id == meId
                    val r = nodeRadius(node)
                    val nodeColor = if (isMe) meColor else when (node.category) {
                        RelCategory.FAMILY -> familyColor
                        RelCategory.FRIEND -> friendColor
                        RelCategory.WORK   -> workColor
                        RelCategory.OTHER  -> otherColor
                    }
                    val bitmap = bitmaps[node.id]
                    if (bitmap != null) {
                        drawCircle(color = nodeColor, radius = r + borderPx, center = drawPos)
                        drawContext.canvas.nativeCanvas.save()
                        drawContext.canvas.nativeCanvas.clipPath(
                            android.graphics.Path().apply {
                                addCircle(drawPos.x, drawPos.y, r, android.graphics.Path.Direction.CW)
                            }
                        )
                        drawContext.canvas.nativeCanvas.drawBitmap(
                            bitmap, null,
                            android.graphics.RectF(drawPos.x - r, drawPos.y - r, drawPos.x + r, drawPos.y + r),
                            photoPaint,
                        )
                        drawContext.canvas.nativeCanvas.restore()
                    } else {
                        drawCircle(color = nodeColor, radius = r, center = drawPos)
                        val onColor = if (isMe) onMeColor else when (node.category) {
                            RelCategory.FAMILY -> onFamilyColor
                            RelCategory.FRIEND -> onFriendColor
                            RelCategory.WORK   -> onWorkColor
                            RelCategory.OTHER  -> onOtherColor
                        }
                        initialsPaint.textSize = r * 0.75f
                        initialsPaint.color = onColor.toArgb()
                        val fm = initialsPaint.fontMetrics
                        drawContext.canvas.nativeCanvas.drawText(
                            node.initials,
                            drawPos.x,
                            drawPos.y - (fm.ascent + fm.descent) / 2f,
                            initialsPaint,
                        )
                    }
                }

                // 3. Labels — zoom-based: inner always, middle at 1.2×, outer at 1.8×
                //    Tap on any node also pins its label regardless of zoom level
                nodes.forEachIndexed { idx, node ->
                    val isMe = node.id == meId
                    if (!isMe && node.category !in activeCategories) return@forEachIndexed
                    val pos = positions[node.id] ?: return@forEachIndexed
                    val ring = if (isMe) -1 else positionRing(pos)
                    val showLabel = isMe
                        || ring == 0
                        || (ring == 1 && scale >= 1.2f)
                        || (ring == 2 && scale >= 1.8f)
                        || node.id == labelNodeId
                    if (!showLabel) return@forEachIndexed
                    val phase = idx * 1.1f
                    val drawPos = if (!animationEnabled || node.id == meId) pos else pos + Offset(
                        sin(floatProgress + phase) * floatAmplitude,
                        cos(floatProgress * 0.73f + phase) * floatAmplitude,
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        if (isMe) "You" else node.name,
                        drawPos.x,
                        drawPos.y + nodeRadius(node) + labelPaint.textSize,
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
            nodes            = nodes,
            positions        = positions,
            meId             = meId,
            activeCategories = RelCategory.entries.toSet(),
            animationEnabled = true,
            onNodeTap        = {},
        )
    }
}
