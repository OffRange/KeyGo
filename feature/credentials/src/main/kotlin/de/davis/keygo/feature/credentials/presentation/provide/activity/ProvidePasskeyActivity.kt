package de.davis.keygo.feature.credentials.presentation.provide.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.provider.PendingIntentHandler
import androidx.fragment.app.FragmentActivity
import de.davis.keygo.core.security.presentation.rememberBiometricCryptoController
import de.davis.keygo.core.ui.theme.KeyGoTheme
import de.davis.keygo.core.util.onFailure
import de.davis.keygo.core.util.onSuccess
import de.davis.keygo.core.util.presentation.ObserveAsEvents
import org.koin.androidx.viewmodel.ext.android.viewModel

internal class ProvidePasskeyActivity : FragmentActivity() {

    private val viewModel by viewModel<ProvidePasskeyViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val request = PendingIntentHandler.retrieveProviderGetCredentialRequest(intent)
        val publicKeyRequest = request
            ?.credentialOptions
            ?.firstOrNull { it is GetPublicKeyCredentialOption } as? GetPublicKeyCredentialOption
            ?: return cancel("No valid GetPublicKeyCredentialOption found")

        val credentialId = intent.extras?.getByteArray(EXTRA_CREDENTIAL_ID)
            ?: return cancel("No credential ID found")

        viewModel.updateGetPublicKeyCredentialOption(publicKeyRequest, credentialId)

        setResult(RESULT_CANCELED)
        setContent {
            KeyGoTheme {
                val biometricCryptoController = rememberBiometricCryptoController()

                BackHandler { cancel() }

                ObserveAsEvents(flow = viewModel.biometricRequest) {
                    biometricCryptoController.requestDecryption(
                        keyId = it.keyId,
                        ciphertextData = it.ciphertextData,
                        policy = it.policy
                    ).onSuccess(viewModel::onPasskeyDecrypted)
                        .onFailure { error -> cancel("Biometric authentication failed: $error") }
                }

                ObserveAsEvents(flow = viewModel.event) {
                    when (it) {
                        is ProvidePasskeyEvent.Abort -> cancel("Operation aborted")
                        is ProvidePasskeyEvent.Finish -> finishWithSuccess(it.responseJson)

                        else -> {}
                    }
                }
            }
        }
    }

    private fun finishWithSuccess(responseJson: String) {
        val passkeyCredential = PublicKeyCredential(responseJson)
        val response = GetCredentialResponse(passkeyCredential)

        val result = Intent()
        PendingIntentHandler.setGetCredentialResponse(
            result,
            response
        )
        setResult(RESULT_OK, result)
        finish()
    }

    private fun cancel(errorMsg: String? = null) {
        val result = errorMsg?.let {
            val response = Intent()
            PendingIntentHandler.setGetCredentialException(
                response,
                GetCredentialUnknownException(errorMsg)
            )
            response
        }

        setResult(RESULT_CANCELED, result)
        finish()
    }

    companion object {

        private const val EXTRA_CREDENTIAL_ID =
            "de.davis.keygo.feature.credentials.extras.CREDENTIAL_ID"
        private const val ACTION_PROVIDE_PASSKEY =
            "de.davis.keygo.feature.credentials.action.PROVIDE_PASSKEY"

        fun getIntent(credentialId: ByteArray, applicationContext: Context): Intent =
            Intent(ACTION_PROVIDE_PASSKEY).apply {
                `package` = applicationContext.packageName

                putExtra(EXTRA_CREDENTIAL_ID, credentialId)
                // TODO: Add when introducing multiple accounts
                //  putExtra(EXTRA_KEY_ACCOUNT_ID, accountId)
                //  also make requestCodes unique per account (see PendingIntent below)
            }
    }
}