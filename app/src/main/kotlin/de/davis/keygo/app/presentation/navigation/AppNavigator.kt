package de.davis.keygo.app.presentation.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import de.davis.keygo.core.presentation.model.RouteDestination

/**
 * Handles navigation events by updating [AppNavigationState]. Everything the UI can do to the back
 * stacks goes through here, so the rules for what replaces what live in one place.
 */
class AppNavigator(val state: AppNavigationState) {

    fun navigate(route: NavKey) {
        val isTopLevel = !state.isLaunching && route in state.backStacks
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

    /** Replaces the launch flow with [route], so back from it leaves the app. */
    fun replaceLaunchFlow(route: NavKey) {
        state.launchStack.clear()
        state.launchStack.add(route)
    }

    /** Ends the launch flow and hands the window to the app proper. */
    fun finishLaunchFlow() {
        state.launchStack.clear()
    }

    /** Adds [route] to the launch flow without disturbing whatever is already on it. */
    fun pushOntoLaunchFlow(route: NavKey) {
        state.launchStack.add(route)
    }

    /**
     * Hides every tab behind [gate] and blocks all back navigation until [unlock] is called. Every
     * tab other than the one currently selected is truncated to its base, tearing down whatever
     * ViewModels it held; the selected tab, and anything already on the launch stack (an
     * in-progress TOTP import, say), are left exactly as they were, restored for free once the gate
     * lifts.
     *
     * A no-op if already gated. This matters because the caller's trigger is collected by a
     * `LaunchedEffect` that re-fires on every fresh composition - including one rebuilt by a
     * configuration change while the app is still locked - with no memory of having already run;
     * [AppNavigationState.isGated] is durable across that rebuild, so it is what keeps a second
     * call from pushing a second gate.
     */
    fun lock(gate: NavKey) {
        if (state.isGated) return
        val activeRoute = state.topLevelRoute
        state.backStacks.forEach { (route, stack) -> if (route != activeRoute) stack.popToBase() }
        pushOntoLaunchFlow(gate)
        state.isGated = true
    }

    /**
     * Lifts the gate [lock] put up, revealing whatever was underneath it.
     *
     * Pops unconditionally rather than only while gated. This is also what dismisses the cold-start
     * auth screen and the onboarding screen, neither of which [lock] ever gated - they are on the
     * launch stack because they were the launch route. Returning early on `!isGated` would leave
     * both up for good after a successful first login.
     */
    fun unlock() {
        state.launchStack.removeLastOrNull()
        state.isGated = false
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
     * The launch stack can hold more than one entry while gated (a picker preserved under the
     * gate, say), so a plain depth check would let back press pop the gate itself away.
     */
    fun goBack() {
        if (state.isGated) return
        val stack = state.currentStack
        if (stack.size > 1) stack.removeLastOrNull()
    }
}

private fun NavBackStack<NavKey>.popToBase() {
    while (size > 1) removeLastOrNull()
}
