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
