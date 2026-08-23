package de.davis.keygo.feature.totp.presentation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import de.davis.keygo.core.ui.RouteDestination
import de.davis.keygo.core.ui.model.PendingTotpImport
import de.davis.keygo.feature.totp.presentation.component.TotpParseErrorDialog
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Where a deep-linked code lands first, before anything else in the app sees it.
 *
 * The code travels as the two halves the deep link splits it into rather than as an assembled uri,
 * because `otpauth://totp/{totpInfo}?{queries}` is the shape the navigation pattern matches.
 */
@Serializable
data class TotpImportRedirect(
    val totpInfo: String? = null,
    val queries: String? = null,
) : RouteDestination {
    val pendingImport: PendingTotpImport
        get() = PendingTotpImport(totpInfo, queries)
}

/**
 * Validates the code a deep link carried and lets the caller decide where a good one leads.
 *
 * The destination is the caller's to pick: auth and onboarding are the two ways into the app, and
 * this module knows about neither.
 *
 * @param onValidated the code parses, so the import is worth an authentication. It carries the code
 * onward so the caller can hand it to whichever screen it sends the user to.
 * @param onRejected the code is unusable and the user has acknowledged it. The app was launched only
 * to import that code, so nothing is left to do. Closing belongs to whoever owns the Activity, which
 * is not this module.
 */
fun NavGraphBuilder.totpImportRedirectGraph(
    onValidated: (PendingTotpImport) -> Unit,
    onRejected: () -> Unit,
) {
    composable<TotpImportRedirect>(
        deepLinks = listOf(
            navDeepLink<TotpImportRedirect>(basePath = PendingTotpImport.BASE_PATH) {
                uriPattern = PendingTotpImport.URI_PATTERN
            },
        ),
    ) { entry ->
        val route = entry.toRoute<TotpImportRedirect>()
        val viewModel: TotpImportRedirectViewModel =
            koinViewModel { parametersOf(route.pendingImport) }
        val state by viewModel.state.collectAsStateWithLifecycle()

        when (state) {
            TotpImportRedirectState.Validating -> Unit

            TotpImportRedirectState.Valid -> LaunchedEffect(route) {
                onValidated(route.pendingImport)
            }

            TotpImportRedirectState.Invalid -> TotpParseErrorDialog(
                onDismiss = onRejected,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
