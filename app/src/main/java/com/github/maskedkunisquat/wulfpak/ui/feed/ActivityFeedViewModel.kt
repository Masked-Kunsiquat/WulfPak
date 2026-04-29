package com.github.maskedkunisquat.wulfpak.ui.feed

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.maskedkunisquat.wulfpak.AppApplication
import com.github.maskedkunisquat.wulfpak.core.data.entity.Activity
import com.github.maskedkunisquat.wulfpak.core.data.entity.Interaction
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed class FeedItem {
    abstract val timestamp: Long
    data class InteractionItem(val interaction: Interaction) : FeedItem() {
        override val timestamp get() = interaction.timestamp
    }
    data class ActivityItem(val activity: Activity) : FeedItem() {
        override val timestamp get() = activity.timestamp
    }
}

class ActivityFeedViewModel(app: Application) : AndroidViewModel(app) {

    private val db = getApplication<AppApplication>().db

    val feed = combine(
        db.interactionDao().getAll(),
        db.activityDao().getAll(),
    ) { interactions, activities ->
        buildList {
            interactions.forEach { add(FeedItem.InteractionItem(it)) }
            activities.forEach  { add(FeedItem.ActivityItem(it)) }
        }.sortedByDescending { it.timestamp }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
