package de.davis.keygo.feature.autofill.presentation.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.autofill.Dataset
import android.view.autofill.AutofillManager
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.rememberNavBackStack
import de.davis.keygo.core.identity.presentation.rememberBiometricUnlockAdapter
import de.davis.keygo.core.identity.presentation.useAdapter
import de.davis.keygo.core.security.domain.model.BiometricPolicy
import de.davis.keygo.core.security.presentation.rememberBiometricCryptoController
import de.davis.keygo.core.security.presentation.rememberHandoffLauncher
import de.davis.keygo.core.ui.clipboard.setText
import de.davis.keygo.core.ui.theme.KeyGoTheme
import de.davis.keygo.core.util.onFailure
import de.davis.keygo.core.util.onSuccess
import de.davis.keygo.core.util.presentation.ObserveAsEvents
import de.davis.keygo.feature.auth.presentation.AuthRoute
import de.davis.keygo.feature.autofill.presentation.activity.component.AssociationDialog
import de.davis.keygo.feature.autofill.presentation.activity.component.SmsCodePendingDialog
import de.davis.keygo.feature.autofill.presentation.activity.component.SuspicionDialog
import de.davis.keygo.feature.autofill.presentation.activity.model.AssociationDialogVisibility
import de.davis.keygo.feature.autofill.presentation.activity.model.AutofillEvent
import de.davis.keygo.feature.autofill.presentation.activity.model.AutofillUiEvent
import de.davis.keygo.feature.autofill.presentation.activity.model.SuspicionDialogVisibility
import de.davis.keygo.feature.autofill.presentation.model.Request
import de.davis.keygo.feature.autofill.presentation.model.RequestData
import de.davis.keygo.feature.item.create.presentation.password.GeneratePasswordModalBottomSheet
import org.koin.androidx.compose.koinViewModel
import de.davis.keygo.core.item.R as CoreItemR


/**
 * This activity is transparent and does not show up in the recent apps list. It is used to gather
 * the right data that should be used for autofill. If needed this activity can show a dialog or
 * request biometrics to authenticate the user.
 *
 * The transparency allows us to have a more integrated user experience, as the user doesn't see a
 * dedicated UI just to authenticate themselves or to show a dialog.
 */
internal class AutofillActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set default result, that is being sent if the user leaves the activity without making
        // a selection.
        setResult(RESULT_CANCELED)
        setContent {
            KeyGoTheme {
                val viewModel = koinViewModel<AutofillViewModel>()
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val dialogVisibility = uiState.associationDialogVisibility
                val suspicionDialogVisibility = uiState.suspicionDialogVisibility

                val biometricCryptoController = rememberBiometricCryptoController()
                val biometricUnlockAdapter = rememberBiometricUnlockAdapter()

                val clipboard = LocalClipboard.current
                val passwordLabel = stringResource(CoreItemR.string.password)

                val smsConsentLauncher = rememberHandoffLauncher(
                    ActivityResultContracts.StartIntentSenderForResult(),
                ) { result ->
                    viewModel.onEvent(
                        AutofillUiEvent.OnSmsConsentResult(result.resultCode == RESULT_OK),
                    )
                }

                ObserveAsEvents(viewModel.events) { event ->
                    when (event) {
                        AutofillEvent.Abort -> cancel()
                        is AutofillEvent.Fill -> {
                            event.copyToClipboard?.let {
                                clipboard.setText(
                                    label = passwordLabel,
                                    text = it,
                                    sensitive = true,
                                )
                            }

                            finishWithResult(event.dataset)
                        }

                        is AutofillEvent.RequestSmsConsent ->
                            smsConsentLauncher.launch(
                                IntentSenderRequest.Builder(event.intentSender).build(),
                            )
                    }
                }

                ObserveAsEvents(viewModel.biometricFlow) { request ->
                    biometricUnlockAdapter.useAdapter {
                        biometricCryptoController.requestUnlockVault(
                            policy = BiometricPolicy(
                                title = request.title,
                                negativeButton = request.negativeButton
                            )
                        )
                    }.onSuccess {
                        viewModel.onBiometricLoginSucceeded()
                    }.onFailure {
                        viewModel.onBiometricLoginFailed(it)
                    }
                }

                LaunchedEffect(Unit) {
                    viewModel.start()
                }

                if (uiState.request !is Request.None) {
                    val backStack = rememberNavBackStack(
                        AuthRoute(
                            showBiometricPromptIfPossible =
                                uiState.request !is Request.JustAuthenticateWithPwd,
                        ),
                    )

                    AutofillUi(
                        backStack = backStack,
                        onItemSelected = { viewModel.onEvent(AutofillUiEvent.OnItemSelected(it)) },
                        onSaved = ::finishWithResult,
                        abort = ::finishWithResult,
                        onAuthenticationSucceeded = {
                            when (val request = uiState.request) {
                                is Request.JustAuthenticateWithPwd -> viewModel.onEvent(
                                    AutofillUiEvent.OnAuthenticated
                                )

                                // The gate is replaced rather than pushed over: back from here
                                // leaves the activity.
                                else -> {
                                    backStack.clear()
                                    backStack.add(request.destination)
                                }
                            }
                        },
                    )
                }

                if (dialogVisibility is AssociationDialogVisibility.Visible)
                    AssociationDialog(
                        itemName = dialogVisibility.itemName,
                        domain = dialogVisibility.domain,
                        onDismissRequest = {},
                        onConfirm = { viewModel.onEvent(AutofillUiEvent.OnAssociate) },
                        onDismiss = { viewModel.onEvent(AutofillUiEvent.OnCancelAssociation) }
                    )

                if (suspicionDialogVisibility is SuspicionDialogVisibility.Visible)
                    SuspicionDialog(
                        onContinue = { viewModel.onEvent(AutofillUiEvent.OnContinueInSuspicion) },
                        onAbort = { viewModel.onEvent(AutofillUiEvent.OnAbortInSuspicion) },
                        appPackageName = suspicionDialogVisibility.appPackageName,
                        website = suspicionDialogVisibility.website
                    )


                if (uiState.showGeneratePassword)
                    GeneratePasswordModalBottomSheet(
                        onGenerated = { viewModel.onEvent(AutofillUiEvent.OnGeneratedPassword(it)) },
                        onDismiss = { viewModel.onEvent(AutofillUiEvent.OnDismissGeneratePassword) }
                    )

                if (uiState.showSmsPending)
                    SmsCodePendingDialog(
                        onCancel = { viewModel.onEvent(AutofillUiEvent.OnCancelSmsCode) }
                    )
            }
        }
    }

    private fun cancel() {
        setResult(RESULT_CANCELED)
        finish()
    }

    private fun finishWithResult(dataset: Dataset? = null) {
        setResult(
            RESULT_OK,
            dataset?.let {
                Intent().apply {
                    putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, it)
                }
            }
        )
        finish()
    }

    companion object {

        fun newIntent(context: Context, requestData: RequestData): Intent = Intent(
            context,
            AutofillActivity::class.java
        ).apply {
            putExtra(
                AutofillViewModel.KEY_AUTOFILL_INFORMATION,
                requestData
            )
        }
    }
}