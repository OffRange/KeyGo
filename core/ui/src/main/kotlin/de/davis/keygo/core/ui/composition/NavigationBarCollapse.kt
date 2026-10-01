package de.davis.keygo.core.ui.composition

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.filterNotNull

@Stable
class NavigationBarCollapseState {

    private class Follower(val state: ScrollableState)

    private var follower by mutableStateOf<Follower?>(null)

    private var scrolledForward by mutableStateOf(false)

    private var leftScrolledAway by mutableStateOf(false)

    // Derived from the list's position rather than accumulated from scroll deltas, so content
    // that changes without a scroll (a delete, a filter) can never leave the bar stuck hidden.
    val isCollapsed: Boolean
        get() = scrolledForward && (follower?.state?.canScrollBackward ?: leftScrolledAway)

    fun reset() {
        scrolledForward = false
        leftScrolledAway = false
    }

    internal suspend fun follow(state: ScrollableState) {
        val current = Follower(state)
        follower = current
        try {
            // A fling that runs into the end keeps scrolling by zero, which clears both flags.
            // Only a scroll that moved the list changes the direction.
            snapshotFlow {
                when {
                    state.lastScrolledForward -> true
                    state.lastScrolledBackward -> false
                    else -> null
                }
            }.filterNotNull().collect { if (follower === current) scrolledForward = it }
        } finally {
            // The chrome comes back a frame before a list returning from an item view does, so
            // the gap keeps the position the list left at instead of flashing the bar.
            if (follower === current) {
                leftScrolledAway = state.canScrollBackward
                follower = null
            }
        }
    }
}

val LocalNavigationBarCollapseState = staticCompositionLocalOf<NavigationBarCollapseState?> { null }

@Composable
fun NavigationBarCollapseEffect(state: ScrollableState, enabled: Boolean = true) {
    val collapseState = LocalNavigationBarCollapseState.current ?: return
    if (enabled) LaunchedEffect(collapseState, state) { collapseState.follow(state) }
}
