package com.github.maskedkunisquat.wulfpak.core.logic.graph

import com.github.maskedkunisquat.wulfpak.core.data.entity.RelCategory
import java.util.UUID

data class GraphEdge(
    val fromId: UUID,
    val toId: UUID,
    val category: RelCategory,
    val closenessScore: Float,
)
