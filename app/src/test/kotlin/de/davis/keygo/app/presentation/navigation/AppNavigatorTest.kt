package de.davis.keygo.app.presentation.navigation

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.core.presentation.model.RouteDestination
import de.davis.keygo.feature.auth.presentation.AuthRoute
import de.davis.keygo.feature.onboarding.presentation.OnboardingRoute
import de.davis.keygo.feature.settings.presentation.ChangePasswordRoute
import de.davis.keygo.feature.settings.presentation.SettingsRoute
import de.davis.keygo.feature.totp.presentation.SelectItemForTotpRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppNavigatorTest {

    private fun navigator(launchRoute: NavKey = AuthRoute()): AppNavigator {
        val state = AppNavigationState(
            overlayStack = NavBackStack(launchRoute),
            topLevelRoute = mutableStateOf(RouteDestination.Home),
            backStacks = TOP_LEVEL_ROUTES.associateWith { NavBackStack<NavKey>(it) },
        )
        return AppNavigator(state)
    }

    private val AppNavigator.shown: List<NavKey>
        get() = if (state.isOverlaid) state.overlayStack.toList()
        else state.backStacks.getValue(state.topLevelRoute).toList()

    // ---- the overlay ----

    @Test
    fun `the overlay owns the window until it is cleared`() {
        val navigator = navigator()

        assertTrue(navigator.state.isOverlaid)
        assertEquals(listOf(AuthRoute()), navigator.shown)

        navigator.clearOverlay()

        assertFalse(navigator.state.isOverlaid)
        assertEquals(listOf(RouteDestination.Home), navigator.shown)
    }

    @Test
    fun `a validated code sends an account with access to the unlock gate`() {
        val navigator = navigator(launchRoute = OnboardingRoute())

        navigator.openGateFor(hasAccess = true, uri = DEEP_LINK_URI)

        assertEquals(listOf(AuthRoute(uri = DEEP_LINK_URI)), navigator.shown)
    }

    @Test
    fun `a validated code sends an account without access to first run`() {
        val navigator = navigator()

        navigator.openGateFor(hasAccess = false, uri = DEEP_LINK_URI)

        assertEquals(listOf(OnboardingRoute(uri = DEEP_LINK_URI)), navigator.shown)
    }

    @Test
    fun `the picker replaces the gate, so back leaves the app`() {
        val navigator = navigator()

        navigator.replaceOverlay(SelectItemForTotpRoute(DEEP_LINK_URI))

        assertEquals(listOf(SelectItemForTotpRoute(DEEP_LINK_URI)), navigator.shown)
        assertEquals(1, navigator.shown.size)
    }

    @Test
    fun `assigning a code pushes onto the overlay and back returns to the picker`() {
        val navigator = navigator()
        navigator.replaceOverlay(SelectItemForTotpRoute(DEEP_LINK_URI))

        navigator.navigate(TestAssignRoute)
        assertEquals(listOf(SelectItemForTotpRoute(DEEP_LINK_URI), TestAssignRoute), navigator.shown)

        navigator.goBack()
        assertEquals(listOf(SelectItemForTotpRoute(DEEP_LINK_URI)), navigator.shown)
    }

    @Test
    fun `back never empties the overlay, so the app is what exits`() {
        val navigator = navigator()

        navigator.goBack()

        assertTrue(navigator.state.isOverlaid)
        assertEquals(listOf(AuthRoute()), navigator.shown)
    }

    @Test
    fun `a top level route is not switched to while the overlay owns the window`() {
        val navigator = navigator()

        navigator.navigate(SettingsRoute)

        assertTrue(navigator.state.isOverlaid)
        assertEquals(listOf(AuthRoute(), SettingsRoute), navigator.shown)
    }

    // ---- top level routes ----

    @Test
    fun `a top level route is the whole of what is shown, with nothing underneath it`() {
        val navigator = unlocked()

        navigator.navigate(SettingsRoute)

        assertEquals(SettingsRoute, navigator.state.topLevelRoute)
        assertEquals(listOf(SettingsRoute), navigator.shown)
    }

    @Test
    fun `coming back to a top level route lands where it was left`() {
        val navigator = unlocked()
        navigator.navigate(SettingsRoute)
        navigator.navigate(ChangePasswordRoute)

        navigator.navigate(RouteDestination.Home)
        assertEquals(listOf(RouteDestination.Home), navigator.shown)

        navigator.navigate(SettingsRoute)
        assertEquals(listOf(SettingsRoute, ChangePasswordRoute), navigator.shown)
    }

    @Test
    fun `each top level route keeps a history of its own`() {
        val navigator = unlocked()
        val itemId = newItemId()
        navigator.showDetail(RouteDestination.ViewItem(itemId))

        navigator.navigate(SettingsRoute)
        navigator.navigate(ChangePasswordRoute)
        assertEquals(listOf(SettingsRoute, ChangePasswordRoute), navigator.shown)

        navigator.navigate(RouteDestination.Home)
        assertEquals(
            listOf(RouteDestination.Home, RouteDestination.ViewItem(itemId)),
            navigator.shown,
        )
    }

    @Test
    fun `back after switching tabs walks the history that tab kept`() {
        val navigator = unlocked()
        navigator.navigate(SettingsRoute)
        navigator.navigate(ChangePasswordRoute)
        navigator.navigate(RouteDestination.Home)

        navigator.navigate(SettingsRoute)
        navigator.goBack()

        assertEquals(listOf(SettingsRoute), navigator.shown)
    }

    @Test
    fun `picking the top level route already shown pops back to its base`() {
        val navigator = unlocked()
        navigator.navigate(SettingsRoute)
        navigator.navigate(ChangePasswordRoute)

        navigator.navigate(SettingsRoute)

        assertEquals(listOf(SettingsRoute), navigator.shown)
    }

    @Test
    fun `back at the base of a top level route leaves the stack for the display to exit through`() {
        val navigator = unlocked()
        navigator.navigate(SettingsRoute)
        navigator.navigate(ChangePasswordRoute)

        navigator.goBack()
        assertEquals(listOf(SettingsRoute), navigator.shown)

        navigator.goBack()
        assertEquals(SettingsRoute, navigator.state.topLevelRoute)
        assertEquals(listOf(SettingsRoute), navigator.shown)
    }

    // ---- the detail pane ----

    @Test
    fun `picking another item swaps the detail instead of stacking one behind it`() {
        val navigator = unlocked()
        val first = newItemId()
        val second = newItemId()

        navigator.showDetail(RouteDestination.ViewItem(first))
        navigator.showDetail(RouteDestination.ViewItem(second))

        assertEquals(
            listOf(RouteDestination.Home, RouteDestination.ViewItem(second)),
            navigator.shown,
        )
    }

    @Test
    fun `editing stacks on the item it edits, so back returns to it`() {
        val navigator = unlocked()
        val itemId = newItemId()
        navigator.showDetail(RouteDestination.ViewItem(itemId))

        navigator.openOnTopOfDetail(RouteDestination.EditItem(VaultItemType.Login, itemId))

        assertEquals(
            listOf(
                RouteDestination.Home,
                RouteDestination.ViewItem(itemId),
                RouteDestination.EditItem(VaultItemType.Login, itemId),
            ),
            navigator.shown,
        )

        navigator.goBack()
        assertEquals(
            listOf(RouteDestination.Home, RouteDestination.ViewItem(itemId)),
            navigator.shown,
        )
    }

    @Test
    fun `closing the detail leaves the list, however deep the detail went`() {
        val navigator = unlocked()
        val itemId = newItemId()
        navigator.showDetail(RouteDestination.ViewItem(itemId))
        navigator.openOnTopOfDetail(RouteDestination.EditItem(VaultItemType.Login, itemId))

        navigator.closeDetail()

        assertEquals(listOf(RouteDestination.Home), navigator.shown)
    }

    @Test
    fun `a narrowing window drops a detail the list picked, but not a form`() {
        val navigator = unlocked()
        navigator.showDetail(RouteDestination.ViewItem(newItemId()))

        navigator.dropAutoSelectedDetail()
        assertEquals(listOf(RouteDestination.Home), navigator.shown)

        navigator.showDetail(RouteDestination.CreateItem(VaultItemType.Login))
        navigator.dropAutoSelectedDetail()

        assertEquals(
            listOf(RouteDestination.Home, RouteDestination.CreateItem(VaultItemType.Login)),
            navigator.shown,
        )
    }

    @Test
    fun `a dialog over the detail leaves the pane reporting what it shows`() {
        val navigator = unlocked()
        val itemId = newItemId()
        navigator.showDetail(RouteDestination.ViewItem(itemId))

        navigator.navigate(RouteDestination.SelectItemType)

        // The list reads this to decide whether to pick a row itself. Reading nothing here makes
        // it pick one, and that lands on top of the dialog and closes it.
        assertEquals(RouteDestination.ViewItem(itemId), navigator.state.openDetail)
        assertEquals(
            listOf(
                RouteDestination.Home,
                RouteDestination.ViewItem(itemId),
                RouteDestination.SelectItemType,
            ),
            navigator.shown,
        )
    }

    @Test
    fun `back at the start route leaves the stack alone for the display to exit through`() {
        val navigator = unlocked()

        navigator.goBack()

        assertEquals(listOf(RouteDestination.Home), navigator.shown)
    }

    // ---- locking ----

    @Test
    fun `locking leaves every tab exactly as it was`() {
        val navigator = unlocked()
        navigator.navigate(SettingsRoute)
        navigator.navigate(ChangePasswordRoute)
        navigator.navigate(RouteDestination.Home)
        val itemId = newItemId()
        navigator.showDetail(RouteDestination.ViewItem(itemId))

        navigator.lock()

        assertEquals(
            listOf(RouteDestination.Home, RouteDestination.ViewItem(itemId)),
            navigator.state.backStacks.getValue(RouteDestination.Home).toList(),
        )
        assertEquals(
            listOf(SettingsRoute, ChangePasswordRoute),
            navigator.state.backStacks.getValue(SettingsRoute).toList(),
        )
    }

    @Test
    fun `locking pushes the gate without clearing what was already on the overlay`() {
        val navigator = navigator()
        navigator.replaceOverlay(SelectItemForTotpRoute(DEEP_LINK_URI))

        navigator.lock()

        assertEquals(
            listOf(SelectItemForTotpRoute(DEEP_LINK_URI), AuthRoute()),
            navigator.shown,
        )
    }

    @Test
    fun `locking from a tab pushes the gate onto an otherwise empty overlay`() {
        val navigator = unlocked()
        navigator.navigate(SettingsRoute)

        navigator.lock()

        assertTrue(navigator.state.isOverlaid)
        assertEquals(listOf(AuthRoute()), navigator.shown)
    }

    @Test
    fun `unlocking reveals the active tab exactly as it was`() {
        val navigator = unlocked()
        navigator.navigate(SettingsRoute)
        navigator.navigate(ChangePasswordRoute)
        navigator.lock()

        navigator.unlock()

        assertFalse(navigator.state.isOverlaid)
        assertEquals(listOf(SettingsRoute, ChangePasswordRoute), navigator.shown)
    }

    @Test
    fun `unlocking reveals a picker that was preserved under the gate`() {
        val navigator = navigator()
        navigator.replaceOverlay(SelectItemForTotpRoute(DEEP_LINK_URI))
        navigator.lock()

        navigator.unlock()

        assertEquals(listOf(SelectItemForTotpRoute(DEEP_LINK_URI)), navigator.shown)
    }

    @Test
    fun `back cannot pop the gate away, even over a picker underneath it`() {
        val navigator = navigator()
        navigator.replaceOverlay(SelectItemForTotpRoute(DEEP_LINK_URI))
        navigator.lock()

        navigator.goBack()

        assertEquals(
            listOf(SelectItemForTotpRoute(DEEP_LINK_URI), AuthRoute()),
            navigator.shown,
        )
    }

    @Test
    fun `back works normally again once unlocked`() {
        val navigator = unlocked()
        navigator.navigate(SettingsRoute)
        navigator.navigate(ChangePasswordRoute)
        navigator.lock()
        navigator.unlock()

        navigator.goBack()

        assertEquals(listOf(SettingsRoute), navigator.shown)
    }

    @Test
    fun `unlocking a cold start gate hands the window to the app proper`() {
        // The cold-start screen is on the overlay as the launch route, not because lock() gated
        // it - but it is an AuthRoute all the same, so unlock() pops it.
        val navigator = navigator()

        navigator.unlock()

        assertFalse(navigator.state.isOverlaid)
        assertEquals(listOf(RouteDestination.Home), navigator.shown)
    }

    @Test
    fun `finishing first run hands the window to the app proper`() {
        // First run is not a gate, so unlock() would refuse it. It is cleared instead.
        val navigator = navigator(launchRoute = OnboardingRoute())

        navigator.finishFirstRun(totpUri = null)

        assertFalse(navigator.state.isOverlaid)
        assertEquals(listOf(RouteDestination.Home), navigator.shown)
    }

    @Test
    fun `a code carried through first run opens its picker`() {
        val navigator = navigator(launchRoute = OnboardingRoute(uri = DEEP_LINK_URI))

        navigator.finishFirstRun(DEEP_LINK_URI)

        assertEquals(listOf(SelectItemForTotpRoute(DEEP_LINK_URI)), navigator.shown)
    }

    @Test
    fun `a code carried through the unlock opens its picker`() {
        val navigator = navigator(launchRoute = AuthRoute(uri = DEEP_LINK_URI))

        navigator.finishUnlock(DEEP_LINK_URI)

        assertEquals(listOf(SelectItemForTotpRoute(DEEP_LINK_URI)), navigator.shown)
    }

    @Test
    fun `a gate restored from saved state is not pushed a second time`() {
        val state = AppNavigationState(
            overlayStack = NavBackStack(AuthRoute()),
            topLevelRoute = mutableStateOf(RouteDestination.Home),
            backStacks = TOP_LEVEL_ROUTES.associateWith { NavBackStack<NavKey>(it) },
        )
        val navigator = AppNavigator(state)

        navigator.lock()

        assertEquals(listOf(AuthRoute()), navigator.shown)
    }

    @Test
    fun `back still cannot pop a gate restored from saved state, even over a preserved picker`() {
        val state = AppNavigationState(
            overlayStack = NavBackStack(SelectItemForTotpRoute(DEEP_LINK_URI), AuthRoute()),
            topLevelRoute = mutableStateOf(RouteDestination.Home),
            backStacks = TOP_LEVEL_ROUTES.associateWith { NavBackStack<NavKey>(it) },
        )
        val navigator = AppNavigator(state)

        navigator.goBack()

        assertEquals(
            listOf(SelectItemForTotpRoute(DEEP_LINK_URI), AuthRoute()),
            navigator.shown,
        )
    }

    @Test
    fun `an import restored on the overlay does not block back the way a gate does`() {
        // Out of reach is not the same as back being forbidden: stepping from the import's
        // assign screen back to its picker is ordinary navigation.
        val state = AppNavigationState(
            overlayStack = NavBackStack(SelectItemForTotpRoute(DEEP_LINK_URI), TestAssignRoute),
            topLevelRoute = mutableStateOf(RouteDestination.Home),
            backStacks = TOP_LEVEL_ROUTES.associateWith { NavBackStack<NavKey>(it) },
        )
        val navigator = AppNavigator(state)

        navigator.goBack()

        assertEquals(listOf(SelectItemForTotpRoute(DEEP_LINK_URI)), navigator.shown)
    }

    private fun unlocked(): AppNavigator = navigator().apply { clearOverlay() }

    private companion object {
        val TOP_LEVEL_ROUTES: Set<NavKey> = linkedSetOf(
            RouteDestination.Home,
            RouteDestination.Connectivity,
            SettingsRoute,
        )

        const val DEEP_LINK_URI =
            "otpauth://totp/GitHub:me@github.com?secret=JBSWY3DPEHPK3PXP&issuer=github.com"
    }
}

/** Stands in for a destination pushed on top of the picker, without pulling in its screen. */
private object TestAssignRoute : NavKey
