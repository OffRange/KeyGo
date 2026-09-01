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
     * Goes back one destination, but never down to nothing. The display stops handling back once a
     * stack is one deep, so the app is what closes.
     */
    fun goBack() {
        val stack = state.currentStack
        if (stack.size > 1) stack.removeLastOrNull()
    }
}

private fun NavBackStack<NavKey>.popToBase() {
    while (size > 1) removeLastOrNull()
}
