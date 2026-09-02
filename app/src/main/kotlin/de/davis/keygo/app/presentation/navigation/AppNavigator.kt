package de.davis.keygo.app.presentation.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import de.davis.keygo.core.presentation.model.RouteDestination
import de.davis.keygo.feature.auth.presentation.AuthRoute

/**
 * Handles navigation events by updating [AppNavigationState]. Everything the UI can do to the back
 * stacks goes through here, so the rules for what replaces what live in one place.
 */
class AppNavigator(val state: AppNavigationState) {

    /**
     * True while [lock]'s gate is the overlay's top entry. Derived, so a restored gate reports
     * itself. The type is what decides it: the `otpauth://` import shares this stack, so neither
     * emptiness nor depth tells a gate from an import screen back may legitimately pop.
     */
    private val isGated: Boolean get() = state.overlayStack.lastOrNull() is AuthRoute

    fun navigate(route: NavKey) {
        val isTopLevel = !state.isOverlaid && route in state.backStacks
        if (isTopLevel) selectTopLevel(route)
        else state.currentStack.add(route)
    }

    /**
     * Switches to the top level [route], keeping whatever history it had. Picking the destination
     * already showing is what clears it, popping back to its base. Nothing sits underneath a base,
     * so back from there closes the app.
     */
    private fun selectTopLevel(route: NavKey) {
        if (route == state.topLevelRoute) state.backStacks.getValue(route).popToBase()
        else state.topLevelRoute = route
    }

    /** Replaces the overlay with [route], so back from it leaves the app. */
    fun replaceOverlay(route: NavKey) {
        state.overlayStack.clear()
        state.overlayStack.add(route)
    }

    /** Clears the overlay and hands the window to the app proper. */
    fun clearOverlay() {
        state.overlayStack.clear()
    }

    /** Adds [route] to the overlay without disturbing whatever is already on it. */
    fun pushOntoOverlay(route: NavKey) {
        state.overlayStack.add(route)
    }

    /**
     * Hides what is showing behind an unlock gate and blocks back until [unlock]. Nothing
     * underneath is disturbed or torn down: a screen holding a secret clears it by observing the
     * session, the way ChangePasswordViewModel does.
     *
     * A no-op if already gated. This matters because the caller's trigger is collected by a
     * `LaunchedEffect` that re-fires on every fresh composition - including one rebuilt by a
     * configuration change while the app is still locked - with no memory of having already run.
     * The restored stack is what remembers, so a second call pushes nothing.
     */
    fun lock() {
        if (isGated) return
        pushOntoOverlay(AuthRoute())
    }

    /**
     * Lifts the gate [lock] put up, revealing whatever was underneath it.
     *
     * Pops unconditionally rather than only while gated. This is also what dismisses the cold-start
     * auth screen and the onboarding screen, neither of which [lock] ever gated - they are on the
     * overlay because they were the launch route. Returning early on `!isGated` would leave
     * both up for good after a successful first login.
     */
    fun unlock() {
        state.overlayStack.removeLastOrNull()
    }

    /**
     * Shows [detail] in the dashboard's detail pane, replacing any detail already open, so back
     * from a detail always lands on the list.
     */
    fun showDetail(detail: RouteDestination.Detail) {
        closeDetail()
        state.currentStack.add(detail)
    }

    /** Opens [detail] on top of the detail already showing, so back returns to it. */
    fun openOnTopOfDetail(detail: RouteDestination.Detail) {
        state.currentStack.add(detail)
    }

    /** Closes whatever detail is open, leaving the list. */
    fun closeDetail() {
        val stack = state.currentStack
        while (stack.lastOrNull() is RouteDestination.Detail) stack.removeLastOrNull()
    }

    /**
     * Drops a detail the list picked on the user's behalf. A form is left alone: it may hold typing
     * that is not saved yet.
     */
    fun dropAutoSelectedDetail() {
        val stack = state.currentStack
        if (stack.lastOrNull() is RouteDestination.ViewItem) stack.removeLastOrNull()
    }

    /**
     * Goes back one destination, but never down to nothing, and never while a lock's gate is up.
     * The overlay can hold more than one entry while gated (a picker preserved under the
     * gate, say), so a plain depth check would let back press pop the gate itself away.
     */
    fun goBack() {
        if (isGated) return
        val stack = state.currentStack
        if (stack.size > 1) stack.removeLastOrNull()
    }
}

private fun NavBackStack<NavKey>.popToBase() {
    while (size > 1) removeLastOrNull()
}
