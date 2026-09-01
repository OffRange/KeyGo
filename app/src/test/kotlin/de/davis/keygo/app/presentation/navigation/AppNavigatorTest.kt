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
            launchStack = NavBackStack(launchRoute),
            topLevelRoute = mutableStateOf(RouteDestination.Home),
            backStacks = TOP_LEVEL_ROUTES.associateWith { NavBackStack<NavKey>(it) },
        )
        return AppNavigator(state)
    }

    private val AppNavigator.shown: List<NavKey>
        get() = if (state.isLaunching) state.launchStack.toList()
        else state.backStacks.getValue(state.topLevelRoute).toList()

    // ---- the launch flow ----

    @Test
    fun `the launch flow owns the window until it finishes`() {
        val navigator = navigator()

        assertTrue(navigator.state.isLaunching)
        assertEquals(listOf(AuthRoute()), navigator.shown)

        navigator.finishLaunchFlow()

        assertFalse(navigator.state.isLaunching)
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

        navigator.replaceLaunchFlow(SelectItemForTotpRoute(DEEP_LINK_URI))

        assertEquals(listOf(SelectItemForTotpRoute(DEEP_LINK_URI)), navigator.shown)
        assertEquals(1, navigator.shown.size)
    }

    @Test
    fun `assigning a code pushes onto the launch flow and back returns to the picker`() {
        val navigator = navigator()
        navigator.replaceLaunchFlow(SelectItemForTotpRoute(DEEP_LINK_URI))

        navigator.navigate(TestAssignRoute)
        assertEquals(listOf(SelectItemForTotpRoute(DEEP_LINK_URI), TestAssignRoute), navigator.shown)

        navigator.goBack()
        assertEquals(listOf(SelectItemForTotpRoute(DEEP_LINK_URI)), navigator.shown)
    }

    @Test
    fun `back never empties the launch flow, so the app is what exits`() {
        val navigator = navigator()

        navigator.goBack()

        assertTrue(navigator.state.isLaunching)
        assertEquals(listOf(AuthRoute()), navigator.shown)
    }

    @Test
    fun `a top level route is not switched to while the launch flow is running`() {
        val navigator = navigator()

        navigator.navigate(SettingsRoute)

        assertTrue(navigator.state.isLaunching)
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

    private fun unlocked(): AppNavigator = navigator().apply { finishLaunchFlow() }

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
