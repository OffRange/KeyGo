package de.davis.keygo.core.ui.composition

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

@Stable
class NavigationBarCollapseState {

    private class Follower(val state: ScrollableState) {
        var scrolledForward by mutableStateOf(false)
    }

    private var follower by mutableStateOf<Follower?>(null)

    // Derived from the list's position rather than accumulated from scroll deltas, so content
    // that changes without a scroll (a delete, a filter) can never leave the bar stuck hidden.
    val isCollapsed: Boolean
        get() = follower?.let { it.scrolledForward && it.state.canScrollBackward } == true

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
            }.filterNotNull().collect { current.scrolledForward = it }
        } finally {
            if (follower === current) follower = null
        }
    }
}

val LocalNavigationBarCollapseState = staticCompositionLocalOf<NavigationBarCollapseState?> { null }

fun Modifier.collapsesNavigationBar(state: ScrollableState, enabled: Boolean = true): Modifier =
    this then CollapsesNavigationBarElement(state, enabled)

private data class CollapsesNavigationBarElement(
    val state: ScrollableState,
    val enabled: Boolean,
) : ModifierNodeElement<CollapsesNavigationBarNode>() {

    override fun create() = CollapsesNavigationBarNode(state, enabled)

    override fun update(node: CollapsesNavigationBarNode) = node.update(state, enabled)

    override fun InspectorInfo.inspectableProperties() {
        name = "collapsesNavigationBar"
        properties["state"] = state
        properties["enabled"] = enabled
    }
}

private class CollapsesNavigationBarNode(
    private var state: ScrollableState,
    private var enabled: Boolean,
) : Modifier.Node(), CompositionLocalConsumerModifierNode {

    private var job: Job? = null

    override fun onAttach() = follow()

    override fun onDetach() {
        job = null
    }

    fun update(state: ScrollableState, enabled: Boolean) {
        if (this.state == state && this.enabled == enabled) return

        this.state = state
        this.enabled = enabled
        if (isAttached) follow()
    }

    private fun follow() {
        job?.cancel()
        job = null
        if (!enabled) return

        val collapseState = currentValueOf(LocalNavigationBarCollapseState) ?: return
        job = coroutineScope.launch { collapseState.follow(state) }
    }
}
