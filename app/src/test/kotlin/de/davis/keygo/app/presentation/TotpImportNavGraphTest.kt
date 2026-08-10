package de.davis.keygo.app.presentation

import androidx.core.net.toUri
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.DialogNavigator
import androidx.navigation.createGraph
import androidx.navigation.testing.TestNavHostController
import androidx.navigation.toRoute
import androidx.test.core.app.ApplicationProvider
import de.davis.keygo.core.ui.model.PendingTotpImport
import de.davis.keygo.feature.auth.presentation.AuthRoute
import de.davis.keygo.feature.auth.presentation.authGraph
import de.davis.keygo.feature.onboarding.presentation.OnboardingRoute
import de.davis.keygo.feature.onboarding.presentation.onboardingGraph
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
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
            totpImportRedirectGraph(hasAccess = hasAccess, navigateAndReplace = {})
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
}
