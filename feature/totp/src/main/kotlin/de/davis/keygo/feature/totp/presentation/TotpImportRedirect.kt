package de.davis.keygo.feature.totp.presentation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import de.davis.keygo.feature.totp.presentation.component.TotpParseErrorDialog
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The link travels whole: back stack keys are saved with kotlinx.serialization, and the parser it
 * is handed to wants the uri rather than its parts.
 *
 * Null when [TotpImportDeepLinkMatcher] matched a link that carried no complete uri, which the
 * redirect screen reports as a parse error instead of swallowing.
 */
@Serializable
data class TotpImportRedirect(val uri: String? = null) : NavKey

fun EntryProviderScope<NavKey>.totpImportRedirectEntries(
    metadata: Map<String, Any> = emptyMap(),
    onValidated: (String) -> Unit,
    onRejected: () -> Unit,
) {
    entry<TotpImportRedirect>(metadata = metadata) { route ->
        val viewModel: TotpImportRedirectViewModel = koinViewModel { parametersOf(route) }
        val state by viewModel.state.collectAsStateWithLifecycle()

        when (val current = state) {
            TotpImportRedirectState.Validating -> Unit

            is TotpImportRedirectState.Valid -> LaunchedEffect(route) {
                onValidated(current.uri)
            }

            TotpImportRedirectState.Invalid -> TotpParseErrorDialog(
                onDismiss = onRejected,
                modifier = Modifier.fillMaxWidth(),
                onDismissRequest = onRejected,
            )
        }
    }
}
