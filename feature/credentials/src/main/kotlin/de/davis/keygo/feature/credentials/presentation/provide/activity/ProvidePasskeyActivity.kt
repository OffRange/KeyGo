package de.davis.keygo.feature.credentials.presentation.provide.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.provider.PendingIntentHandler
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import de.davis.keygo.core.identity.presentation.rememberBiometricUnlockAdapter
import de.davis.keygo.core.identity.presentation.useAdapter
import de.davis.keygo.core.security.domain.model.BiometricPolicy
import de.davis.keygo.core.security.domain.model.BiometricString
import de.davis.keygo.core.security.presentation.rememberBiometricCryptoController
import de.davis.keygo.core.ui.navigation.KeyGoNavDisplay
import de.davis.keygo.core.ui.theme.KeyGoTheme
import de.davis.keygo.core.util.onFailure
import de.davis.keygo.core.util.onSuccess
import de.davis.keygo.core.util.presentation.ObserveAsEvents
import de.davis.keygo.feature.auth.presentation.AuthRoute
import de.davis.keygo.feature.auth.presentation.authEntries
import de.davis.keygo.feature.credentials.presentation.auth.SessionAuthState
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

        viewModel.setRequest(publicKeyRequest, credentialId)

        setResult(RESULT_CANCELED)
        setContent {
            KeyGoTheme {
                BackHandler { cancel() }

                ObserveAsEvents(viewModel.event) {
                    when (it) {
                        is ProvidePasskeyEvent.Abort -> cancel("Operation aborted")
                        is ProvidePasskeyEvent.Finish -> finishWithSuccess(it.responseJson)
                    }
                }

                val biometricCryptoController = rememberBiometricCryptoController()
                val biometricUnlockAdapter = rememberBiometricUnlockAdapter()

                ObserveAsEvents(viewModel.biometricFlow) {
                    biometricUnlockAdapter.useAdapter {
                        biometricCryptoController.requestUnlockVault(
                            policy = BiometricPolicy(
                                title = BiometricString.Title.Authenticate,
                                negativeButton = BiometricString.NegativeButton.Password,
                            )
                        )
                    }.onSuccess {
                        viewModel.onUnlocked()
                    }.onFailure {
                        viewModel.onUnlockFailed(it)
                    }
                }

                val authState by viewModel.authState.collectAsStateWithLifecycle()
                when (authState) {
                    SessionAuthState.TryBiometric -> {
                        // render nothing: activity stays transparent while system biometric prompt is shown
                    }

                    SessionAuthState.NeedsPassword -> {
                        val backStack =
                            rememberNavBackStack(AuthRoute(showBiometricPromptIfPossible = false))

                        KeyGoNavDisplay(
                            backStack = backStack,
                            entryProvider = entryProvider {
                                authEntries(onSuccess = { viewModel.onUnlocked() })
                            },
                        )
                    }

                    SessionAuthState.Authenticated -> {
                        // render nothing: operation runs in ViewModel and emits Finish/Abort
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
