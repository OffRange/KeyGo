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

@Serializable
data class TotpImportRedirect(
    val totpInfo: String? = null,
    val queries: String? = null,
) : RouteDestination {
    val pendingImport: PendingTotpImport
        get() = PendingTotpImport(totpInfo, queries)
}

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
                onDismissRequest = onRejected,
            )
        }
    }
}
