package com.github.maskedkunisquat.wulfpak.core.logic.graph

import com.github.maskedkunisquat.wulfpak.core.data.entity.RelCategory
import java.util.UUID

data class GraphNode(
    val id: UUID,
    val name: String,
    val category: RelCategory,
    val closenessScore: Float?,
    val initials: String = "",
    val photoUri: String? = null,
)
