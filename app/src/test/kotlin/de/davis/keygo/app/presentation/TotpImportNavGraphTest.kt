package de.davis.keygo.app.presentation

import androidx.core.net.toUri
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.DialogNavigator
import androidx.navigation.createGraph
import androidx.navigation.testing.TestNavHostController
import androidx.navigation.toRoute
import androidx.test.core.app.ApplicationProvider
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.ui.model.PendingTotpImport
import de.davis.keygo.feature.auth.presentation.AuthRoute
import de.davis.keygo.feature.auth.presentation.authGraph
import de.davis.keygo.feature.onboarding.presentation.OnboardingRoute
import de.davis.keygo.feature.onboarding.presentation.onboardingGraph
import de.davis.keygo.feature.totp.presentation.TotpImportRedirect
import de.davis.keygo.feature.totp.presentation.totpImportRedirectGraph
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TotpImportNavGraphTest {

    private fun navController(hasAccess: Boolean): TestNavHostController {
        val controller =
            TestNavHostController(ApplicationProvider.getApplicationContext())
        controller.navigatorProvider.addNavigator(ComposeNavigator())
        controller.navigatorProvider.addNavigator(DialogNavigator())

        controller.graph = controller.createGraph(
            startDestination = if (hasAccess) AuthRoute() else OnboardingRoute(),
        ) {
            totpImportRedirectGraph(onValidated = {}, onRejected = {})
            totpImportGraph(
                navigateToDestination = {},
                onImportFinished = {},
                navigateUp = {},
            )
            authGraph(onSuccess = {})
            onboardingGraph(onSuccess = {})
        }

        return controller
    }

    @Test
    fun `graph builds for an account that already has access`() {
        val controller = navController(hasAccess = true)

        assertTrue(controller.currentDestination?.hasRoute<AuthRoute>() == true)
    }

    @Test
    fun `graph builds for an account without access`() {
        val controller = navController(hasAccess = false)

        assertTrue(controller.currentDestination?.hasRoute<OnboardingRoute>() == true)
    }

    @Test
    fun `otpauth deep link resolves to the redirect destination`() {
        val controller = navController(hasAccess = true)

        controller.navigate("otpauth://totp/Example:me@example.com?secret=ABC".toUri())

        val entry = assertNotNull(controller.currentBackStackEntry)
        assertTrue(entry.destination.hasRoute<TotpImportRedirect>())

        val route = entry.toRoute<TotpImportRedirect>()
        assertEquals("Example:me@example.com", route.totpInfo)
        assertEquals("secret=ABC", route.queries)
        assertEquals(
            "otpauth://totp/Example:me@example.com?secret=ABC",
            route.pendingImport.uri,
        )
    }

    @Test
    fun `AuthRoute round trips the pending import through the back stack`() {
        val controller = navController(hasAccess = true)
        val redirect = TotpImportRedirect(
            totpInfo = "Example:me@example.com",
            queries = "secret=ABC",
        )

        controller.navigate(
            AuthRoute(totpInfo = redirect.totpInfo, queries = redirect.queries),
        )

        val route = assertNotNull(controller.currentBackStackEntry).toRoute<AuthRoute>()
        assertEquals(redirect.pendingImport, route.pendingTotpImport)
        assertEquals("otpauth://totp/Example:me@example.com?secret=ABC", route.uri)
    }

    @Test
    fun `OnboardingRoute round trips the pending import through the back stack`() {
        val controller = navController(hasAccess = false)
        val redirect = TotpImportRedirect(
            totpInfo = "Example:me@example.com",
            queries = "secret=ABC",
        )

        controller.navigate(
            OnboardingRoute(totpInfo = redirect.totpInfo, queries = redirect.queries),
        )

        val route = assertNotNull(controller.currentBackStackEntry).toRoute<OnboardingRoute>()
        assertEquals(redirect.pendingImport, route.pendingTotpImport)
        assertEquals("otpauth://totp/Example:me@example.com?secret=ABC", route.uri)
    }

    @Test
    fun `a plain launch carries no pending import`() {
        val controller = navController(hasAccess = true)

        val route = assertNotNull(controller.currentBackStackEntry).toRoute<AuthRoute>()
        assertEquals(PendingTotpImport(), route.pendingTotpImport)
        assertNull(route.uri)
    }

    @Test
    fun `the picker route carries the whole uri`() {
        val controller = navController(hasAccess = true)

        controller.navigate(SelectItemForTotpRoute(DEEP_LINK_URI))

        val entry = assertNotNull(controller.currentBackStackEntry)
        assertTrue(entry.destination.hasRoute<SelectItemForTotpRoute>())
        assertEquals(DEEP_LINK_URI, entry.toRoute<SelectItemForTotpRoute>().totpUri)
    }

    @Test
    fun `choosing an item carries its id to the form`() {
        val controller = navController(hasAccess = true)
        val itemId = newItemId()

        controller.navigate(AssignTotpRoute(DEEP_LINK_URI, itemId.toString()))

        val route = assertNotNull(controller.currentBackStackEntry).toRoute<AssignTotpRoute>()
        assertEquals(DEEP_LINK_URI, route.totpUri)
        assertEquals(itemId, route.selectedItemId)
    }

    @Test
    fun `creating a new item carries no id`() {
        val controller = navController(hasAccess = true)

        controller.navigate(AssignTotpRoute(DEEP_LINK_URI))

        val route = assertNotNull(controller.currentBackStackEntry).toRoute<AssignTotpRoute>()
        assertEquals(DEEP_LINK_URI, route.totpUri)
        assertNull(route.selectedItemId)
    }

    @Test
    fun `the picker replaces the auth entry so back leaves the app`() {
        val controller = navController(hasAccess = true)

        controller.navigate(SelectItemForTotpRoute(DEEP_LINK_URI)) {
            popUpTo<AuthRoute> { inclusive = true }
        }

        assertTrue(controller.currentDestination?.hasRoute<SelectItemForTotpRoute>() == true)
        assertFalse(
            controller.currentBackStack.value.any { it.destination.hasRoute<AuthRoute>() },
        )
    }

    @Test
    fun `a validated code sends an account with access to AuthRoute`() {
        val controller = navController(hasAccess = true)
        controller.navigate(
            TotpImportRedirect(totpInfo = "Example:me@example.com", queries = "secret=ABC"),
        )

        controller.navigateToValidatedImport(
            hasAccess = true,
            pending = PendingTotpImport(totpInfo = "Example:me@example.com", queries = "secret=ABC"),
        )

        assertTrue(controller.currentDestination?.hasRoute<AuthRoute>() == true)

        val route = assertNotNull(controller.currentBackStackEntry).toRoute<AuthRoute>()
        assertEquals("Example:me@example.com", route.totpInfo)
        assertEquals("secret=ABC", route.queries)
        assertFalse(
            controller.currentBackStack.value.any { it.destination.hasRoute<TotpImportRedirect>() },
        )
    }

    @Test
    fun `a validated code sends an account without access to OnboardingRoute`() {
        val controller = navController(hasAccess = false)
        controller.navigate(
            TotpImportRedirect(totpInfo = "Example:me@example.com", queries = "secret=ABC"),
        )

        controller.navigateToValidatedImport(
            hasAccess = false,
            pending = PendingTotpImport(totpInfo = "Example:me@example.com", queries = "secret=ABC"),
        )

        assertTrue(controller.currentDestination?.hasRoute<OnboardingRoute>() == true)

        val route = assertNotNull(controller.currentBackStackEntry).toRoute<OnboardingRoute>()
        assertEquals("Example:me@example.com", route.totpInfo)
        assertEquals("secret=ABC", route.queries)
        assertFalse(
            controller.currentBackStack.value.any { it.destination.hasRoute<TotpImportRedirect>() },
        )
    }

    private companion object {
        const val DEEP_LINK_URI =
            "otpauth://totp/GitHub:me@github.com?secret=JBSWY3DPEHPK3PXP&issuer=github.com"
    }
}
