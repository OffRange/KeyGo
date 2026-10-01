package de.davis.keygo.core.ui.composition

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NavigationBarCollapseStateTest {

    private class FakeScrollableState : ScrollableState {
        override var canScrollBackward by mutableStateOf(false)
        override var lastScrolledForward by mutableStateOf(false)
        override var lastScrolledBackward by mutableStateOf(false)
        override val isScrollInProgress = false

        override fun dispatchRawDelta(delta: Float) = 0f

        override suspend fun scroll(
            scrollPriority: MutatePriority,
            block: suspend ScrollScope.() -> Unit,
        ) = Unit

        fun scrolled(forward: Boolean?) {
            lastScrolledForward = forward == true
            lastScrolledBackward = forward == false
        }
    }

    private val collapseState = NavigationBarCollapseState()
    private val list = FakeScrollableState()

    private fun TestScope.settle() {
        Snapshot.sendApplyNotifications()
        runCurrent()
    }

    private fun TestScope.scrollDownAwayFromTop(list: FakeScrollableState) {
        list.canScrollBackward = true
        list.scrolled(forward = true)
        settle()
    }

    @Test
    fun `collapses once the list scrolls forward away from its top`() = runTest {
        backgroundScope.launch { collapseState.follow(list) }
        settle()
        assertFalse(collapseState.isCollapsed)

        scrollDownAwayFromTop(list)

        assertTrue(collapseState.isCollapsed)
    }

    @Test
    fun `expands on a backward scroll`() = runTest {
        backgroundScope.launch { collapseState.follow(list) }
        scrollDownAwayFromTop(list)

        list.scrolled(forward = false)
        settle()

        assertFalse(collapseState.isCollapsed)
    }

    @Test
    fun `expands when the list stops reaching back without a scroll`() = runTest {
        backgroundScope.launch { collapseState.follow(list) }
        scrollDownAwayFromTop(list)

        list.canScrollBackward = false
        settle()

        assertFalse(collapseState.isCollapsed)
    }

    @Test
    fun `a scroll that moved nothing keeps the last direction`() = runTest {
        backgroundScope.launch { collapseState.follow(list) }
        scrollDownAwayFromTop(list)

        list.scrolled(forward = null)
        settle()

        assertTrue(collapseState.isCollapsed)
    }

    @Test
    fun `stays collapsed between a list leaving scrolled away and coming back`() = runTest {
        val job = backgroundScope.launch { collapseState.follow(list) }
        scrollDownAwayFromTop(list)

        job.cancel()
        settle()
        assertTrue(collapseState.isCollapsed)

        backgroundScope.launch { collapseState.follow(list) }
        settle()
        assertTrue(collapseState.isCollapsed)
    }

    @Test
    fun `expands once a list leaves at its top`() = runTest {
        val job = backgroundScope.launch { collapseState.follow(list) }
        scrollDownAwayFromTop(list)
        list.canScrollBackward = false
        settle()

        job.cancel()
        settle()

        assertFalse(collapseState.isCollapsed)
    }

    @Test
    fun `reset expands with no list followed`() = runTest {
        val job = backgroundScope.launch { collapseState.follow(list) }
        scrollDownAwayFromTop(list)
        job.cancel()
        settle()

        collapseState.reset()

        assertFalse(collapseState.isCollapsed)
    }

    @Test
    fun `a superseded list no longer steers the bar`() = runTest {
        backgroundScope.launch { collapseState.follow(list) }
        scrollDownAwayFromTop(list)

        val newer = FakeScrollableState()
        backgroundScope.launch { collapseState.follow(newer) }
        scrollDownAwayFromTop(newer)

        list.scrolled(forward = false)
        settle()

        assertTrue(collapseState.isCollapsed)
    }
}
