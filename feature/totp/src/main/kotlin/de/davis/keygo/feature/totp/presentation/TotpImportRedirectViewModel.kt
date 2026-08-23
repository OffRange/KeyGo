package de.davis.keygo.feature.totp.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import de.davis.keygo.core.ui.model.PendingTotpImport
import de.davis.keygo.core.util.fold
import de.davis.keygo.rust.totp.TotpService
import de.davis.keygo.rust.totp.getInfoFromUriWithResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel

/**
 * Decides whether a deep-linked code is worth asking the user to authenticate for.
 *
 * The import used to reach the item picker before anything read the code, so a malformed one cost
 * the user a full unlock before it told them anything. This is the single gate that check now
 * passes through, which is why neither the picker nor the login form reports a parse failure of its
 * own any more.
 *
 * The pending import arrives whole rather than as its assembled uri because that uri is null for a
 * structurally incomplete link, and Koin resolves an injected parameter by type, which a null value
 * does not have.
 */
@KoinViewModel
internal class TotpImportRedirectViewModel(
    @InjectedParam private val pendingImport: PendingTotpImport,
    private val totpService: TotpService,
) : ViewModel() {

    private val _state =
        MutableStateFlow<TotpImportRedirectState>(TotpImportRedirectState.Validating)
    val state: StateFlow<TotpImportRedirectState> = _state.asStateFlow()

    init {
        _state.value = validate()
    }

    /**
     * A null uri means the link carried no path or no query string, which leaves as little to
     * import as a code the parser rejects. Both end the import here.
     */
    private fun validate(): TotpImportRedirectState {
        val uri = pendingImport.uri ?: run {
            Log.e(TAG, "Deep link carried no complete otpauth uri")
            return TotpImportRedirectState.Invalid
        }

        return totpService.getInfoFromUriWithResult(uri).fold(
            onSuccess = { TotpImportRedirectState.Valid },
            onFailure = { failure ->
                Log.e(TAG, "Error parsing TOTP URI: $failure")
                TotpImportRedirectState.Invalid
            },
        )
    }

    companion object {
        private const val TAG = "TotpImportRedirectVM"
    }
}
