package com.github.maskedkunisquat.wulfpak.core.data.entity

enum class FamilyRelType(val displayLabel: String, val reverseLabel: String) {
    PARENT_OF("Parent", "Child"),
    SPOUSE_OF("Spouse", "Spouse"),
    SIBLING_OF("Sibling", "Sibling"),
    HALF_SIBLING_OF("Half-sibling", "Half-sibling"),
    STEP_PARENT_OF("Step-parent", "Step-child"),
    GRANDPARENT_OF("Grandparent", "Grandchild"),
}
