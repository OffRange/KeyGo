package de.davis.keygo.app.presentation.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import de.davis.keygo.core.presentation.model.RouteDestination
import de.davis.keygo.feature.auth.presentation.AuthRoute
import de.davis.keygo.feature.onboarding.presentation.OnboardingRoute
import de.davis.keygo.feature.totp.presentation.TotpImportRedirect

/**
 * Handles navigation events by updating [AppNavigationState]. Everything the UI can do to the back
 * stacks goes through here, so the rules for what replaces what live in one place.
 *
 * Every entry point the UI can reach refuses to run while the gate is up. The chrome does not
 * vanish the instant [lock] fires, it animates out, so the navigation bar and the create button
 * stay composed and clickable for a moment behind the gate. Without the guard a tap in that window
 * would push its destination onto the overlay, above the gate, and show it unauthenticated.
 */
class AppNavigator(val state: AppNavigationState) {

    /**
     * True while [lock]'s gate is the overlay's top entry. Derived, so a restored gate reports
     * itself. The type is what decides it: the `otpauth://` import shares this stack, so neither
     * emptiness nor depth tells a gate from an import screen back may legitimately pop.
     */
    private val isGated: Boolean get() = state.overlayStack.lastOrNull() is AuthRoute

    /**
     * True while the overlay is showing a screen that runs before there is a session, so a locked
     * session is what it is there for rather than a reason to gate it. The import flow is not one
     * of these: it only ever runs after an unlock, so a session that ends under it does gate it.
     */
    private val runsWithoutSession: Boolean
        get() = when (state.overlayStack.lastOrNull()) {
            is AuthRoute, is OnboardingRoute, is TotpImportRedirect -> true
            else -> false
        }

    fun navigate(route: NavKey) {
        if (isGated) return
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

    /**
     * Clears the overlay and hands the window to the app proper. Refused while the gate is up:
     * this is the one path that would drop a gate without anything having authenticated, and the
     * screens that call it sit under the gate rather than over it.
     */
    fun clearOverlay() {
        if (isGated) return
        state.overlayStack.clear()
    }

    /** Adds [route] to the overlay without disturbing whatever is already on it. */
    fun pushOntoOverlay(route: NavKey) {
        state.overlayStack.add(route)
    }

    /**
     * Hides what is showing behind an unlock gate and blocks back until [unlock]. Nothing
     * underneath is disturbed or torn down: a screen holding a secret clears it by observing the
     * session, the way ChangePasswordViewModel does. A no-op while a screen that runs without a
     * session is already up, which covers both a gate already in place and the caller re-firing.
     */
    fun lock() {
        if (runsWithoutSession) return
        pushOntoOverlay(AuthRoute())
    }

    /** Lifts the gate. Only gates reach here; first run is taken down with [clearOverlay]. */
    fun unlock() {
        if (!isGated) return
        state.overlayStack.removeLastOrNull()
    }

    /**
     * Shows [detail] in the dashboard's detail pane, replacing any detail already open, so back
     * from a detail always lands on the list.
     */
    fun showDetail(detail: RouteDestination.Detail) {
        if (isGated) return
        closeDetail()
        state.currentStack.add(detail)
    }

    /** Opens [detail] on top of the detail already showing, so back returns to it. */
    fun openOnTopOfDetail(detail: RouteDestination.Detail) {
        if (isGated) return
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
     *
     * Reaches past the overlay to the tab that owns the detail, rather than going through
     * [AppNavigationState.currentStack]. The window can narrow while the gate is up - rotating at
     * the lock screen is an ordinary thing to do - and the overlay's top entry is never a detail,
     * so this would find nothing to drop and the tab would keep a selection the user never made,
     * waiting full screen behind the unlock.
     */
    fun dropAutoSelectedDetail() {
        val stack = state.backStacks.getValue(state.topLevelRoute)
        if (stack.lastOrNull() is RouteDestination.ViewItem) stack.removeLastOrNull()
    }

    /**
     * Goes back one destination, never down to nothing, and never while the gate is up: the
     * overlay can be deeper than one entry then, so a depth check alone would pop the gate.
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
