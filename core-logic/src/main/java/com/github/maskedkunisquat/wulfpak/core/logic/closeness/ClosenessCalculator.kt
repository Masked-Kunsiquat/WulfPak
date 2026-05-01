package com.github.maskedkunisquat.wulfpak.core.logic.closeness

import com.github.maskedkunisquat.wulfpak.core.data.entity.Interaction
import com.github.maskedkunisquat.wulfpak.core.data.entity.InteractionType
import com.github.maskedkunisquat.wulfpak.core.data.entity.RelCategory
import java.util.concurrent.TimeUnit
import kotlin.math.pow

object ClosenessCalculator {

    private fun typeWeight(type: String): Double = when (type) {
        InteractionType.IN_PERSON -> 1.0
        InteractionType.VIDEO_CALL -> 0.8
        InteractionType.CALL -> 0.6
        InteractionType.TEXT -> 0.4
        InteractionType.EMAIL -> 0.3
        InteractionType.SOCIAL_MEDIA -> 0.2
        else -> 0.2
    }

    private fun halfLifeDays(category: String): Double = when (category) {
        RelCategory.FAMILY.name -> 365.0
        RelCategory.FRIEND.name -> 150.0
        RelCategory.WORK.name -> 60.0
        else -> 90.0
    }

    // Theoretical max: 1 year of weekly IN_PERSON 1-hr contacts (typeWeight=1.0, durationBonus=1.0)
    private fun theoreticalMax(halfLife: Double): Double {
        var sum = 0.0
        for (week in 1..52) {
            sum += 2.0 * 2.0.pow(-week * 7.0 / halfLife)
        }
        return sum
    }

    fun compute(interactions: List<Interaction>, category: String): Float {
        if (interactions.isEmpty()) return 0f

        val halfLife = halfLifeDays(category)
        val nowMs = System.currentTimeMillis()
        var rawScore = 0.0

        for (interaction in interactions) {
            val daysAgo = TimeUnit.MILLISECONDS.toDays(nowMs - interaction.timestamp).toDouble()
            if (daysAgo < 0.0) continue

            val weight = typeWeight(interaction.type)
            val durationBonus = minOf((interaction.durationSeconds ?: 0) / 3600.0, 1.0)
            rawScore += (weight + durationBonus) * 2.0.pow(-daysAgo / halfLife)
        }

        return (rawScore / theoreticalMax(halfLife)).coerceIn(0.0, 1.0).toFloat()
    }
}
