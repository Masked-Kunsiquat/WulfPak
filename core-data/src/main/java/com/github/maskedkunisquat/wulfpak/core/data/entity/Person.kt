package com.github.maskedkunisquat.wulfpak.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

object RelationLabel {
    const val MOTHER = "mother"; const val FATHER = "father"
    const val SIBLING = "sibling"; const val CHILD = "child"
    const val GRANDPARENT = "grandparent"; const val COUSIN = "cousin"
    const val AUNT = "aunt"; const val UNCLE = "uncle"
    const val FRIEND = "friend"; const val BEST_FRIEND = "best_friend"
    const val ACQUAINTANCE = "acquaintance"; const val ROMANTIC_PARTNER = "romantic_partner"
    const val COLLEAGUE = "colleague"; const val MANAGER = "manager"
    const val REPORT = "report"; const val MENTOR = "mentor"; const val CLIENT = "client"

    val ALL = listOf(
        FRIEND, BEST_FRIEND, ACQUAINTANCE, ROMANTIC_PARTNER,
        MOTHER, FATHER, SIBLING, CHILD, GRANDPARENT, COUSIN, AUNT, UNCLE,
        COLLEAGUE, MANAGER, REPORT, MENTOR, CLIENT,
    )
}

@Entity(tableName = "persons")
data class Person(
    @PrimaryKey val id: UUID = UUID.randomUUID(),
    val firstName: String,
    val lastName: String? = null,
    val nickname: String? = null,
    val photoUri: String? = null,
    val relationLabel: String,
    val isFavorite: Boolean = false,
    val lastContactedAt: Long? = null,
    val interactionCount: Int = 0,
    val closenessRating: Int? = null,
    val company: String? = null,
    val jobTitle: String? = null,
    val cachedSummary: String? = null,
    val summaryGeneratedAt: Long? = null,
    val closenessScore: Float? = null,
    val isMe: Boolean = false,
)
