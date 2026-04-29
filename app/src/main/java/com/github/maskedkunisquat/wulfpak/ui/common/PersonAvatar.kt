package com.github.maskedkunisquat.wulfpak.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.maskedkunisquat.wulfpak.core.data.entity.Person

@Composable
fun PersonAvatar(person: Person, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val initials = buildString {
        append(person.firstName.firstOrNull() ?: "")
        append(person.lastName?.firstOrNull() ?: "")
    }.uppercase()
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            style = if (size >= 56.dp) MaterialTheme.typography.titleLarge
                    else MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}
