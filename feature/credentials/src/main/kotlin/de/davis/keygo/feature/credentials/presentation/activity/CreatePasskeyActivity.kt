package de.davis.keygo.feature.credentials.presentation.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.exceptions.CreateCredentialUnknownException
import androidx.credentials.provider.PendingIntentHandler
import androidx.fragment.app.FragmentActivity
import de.davis.keygo.core.security.presentation.rememberBiometricCryptoController
import de.davis.keygo.core.ui.theme.KeyGoTheme
import de.davis.keygo.core.util.onSuccess
import de.davis.keygo.core.util.presentation.ObserveAsEvents
import org.koin.androidx.viewmodel.ext.android.viewModel

internal class CreatePasskeyActivity : FragmentActivity() {

    private val viewModel by viewModel<CreatePasskeyViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val request = PendingIntentHandler.retrieveProviderCreateCredentialRequest(intent)
        val callingRequest = request?.callingRequest as? CreatePublicKeyCredentialRequest
            ?: return cancel()

        viewModel.updateCreatePublicKeyCredentialRequest(callingRequest)
        setResult(RESULT_CANCELED)

        setContent {
            KeyGoTheme {
                val biometricCryptoController = rememberBiometricCryptoController()

                ObserveAsEvents(flow = viewModel.biometricRequest) {
                    when (it) {
                        is CreatePasskeyBiometricRequestEvent.UnwrapPasskeyEncryptionKey -> {
                            biometricCryptoController.requestUnwrap(
                                keyInfo = it.keyInfo,
                                wrappedKey = it.wrappedKey,
                                policy = it.policy
                            ).onSuccess(viewModel::passkeyEncryptionKeyUnwrapped)
                        }
                    }
                }

                ObserveAsEvents(flow = viewModel.event) {
                    when (it) {
                        CreatePasskeyEvent.Abort -> cancel()
                    }
                }
            }
        }
    }

    private fun finishWithSuccess(responseJson: String) {
        val createPublicKeyCredResponse = CreatePublicKeyCredentialResponse(responseJson)

        val result = Intent()
        PendingIntentHandler.setCreateCredentialResponse(
            result,
            createPublicKeyCredResponse
        )

        setResult(RESULT_OK, result)
        finish()
    }

    private fun cancel(errorMsg: String? = null) {
        val result = errorMsg?.let {
            val response = Intent()
            PendingIntentHandler.setCreateCredentialException(
                response,
                CreateCredentialUnknownException(errorMsg)
            )
            response
        }

        setResult(RESULT_CANCELED, result)
        finish()
    }
}