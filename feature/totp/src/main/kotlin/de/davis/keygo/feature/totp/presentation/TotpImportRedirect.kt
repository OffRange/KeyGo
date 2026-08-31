package de.davis.keygo.feature.totp.presentation

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import de.davis.keygo.core.ui.model.PendingTotpImport
import de.davis.keygo.feature.totp.presentation.component.TotpParseErrorDialog
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Serializable
data class TotpImportRedirect(
    val totpInfo: String? = null,
    val queries: String? = null,
) : NavKey {
    val pendingImport: PendingTotpImport
        get() = PendingTotpImport(totpInfo, queries)

    companion object {
        fun from(uri: Uri): TotpImportRedirect? {
            if (!uri.scheme.equals(PendingTotpImport.SCHEME, ignoreCase = true)) return null
            if (!uri.host.equals(PendingTotpImport.HOST, ignoreCase = true)) return null

            return TotpImportRedirect(
                totpInfo = uri.path?.removePrefix("/")?.takeIf { it.isNotBlank() },
                queries = uri.query?.takeIf { it.isNotBlank() },
            )
        }
    }
}

fun EntryProviderScope<NavKey>.totpImportRedirectEntries(
    metadata: Map<String, Any> = emptyMap(),
    onValidated: (PendingTotpImport) -> Unit,
    onRejected: () -> Unit,
) {
    entry<TotpImportRedirect>(metadata = metadata) { route ->
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
