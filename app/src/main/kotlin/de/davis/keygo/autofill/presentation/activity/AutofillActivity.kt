package de.davis.keygo.autofill.presentation.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.autofill.Dataset
import android.view.autofill.AutofillManager
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import de.davis.keygo.autofill.presentation.activity.component.AssociationDialog
import de.davis.keygo.autofill.presentation.activity.component.SuspicionDialog
import de.davis.keygo.autofill.presentation.model.AssociationDialogVisibility
import de.davis.keygo.autofill.presentation.model.AutofillEvent
import de.davis.keygo.autofill.presentation.model.AutofillUiEvent
import de.davis.keygo.autofill.presentation.model.Request
import de.davis.keygo.autofill.presentation.model.RequestData
import de.davis.keygo.autofill.presentation.model.SuspicionDialogVisibility
import de.davis.keygo.core.identity.biometric.presentation.BiometricPromptSupport
import de.davis.keygo.core.identity.biometric.presentation.LocalBiometricManager
import de.davis.keygo.core.identity.biometric.presentation.model.BiometricRequest
import de.davis.keygo.core.presentation.ObserveAsEvents
import de.davis.keygo.core.presentation.model.RouteDestination
import de.davis.keygo.core.presentation.theme.KeyGoTheme
import org.koin.androidx.compose.koinViewModel


/**
 * This activity is transparent and does not show up in the recent apps list. It is used to gather
 * the right data that should be used for autofill. If needed this activity can show a dialog or
 * request biometrics to authenticate the user.
 *
 * The transparency allows us to have a more integrated user experience, as the user doesn't see a
 * dedicated UI just to authenticate themself or to show a dialog.
 */
internal class AutofillActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set default result, that is being sent if the user leaves the activity without making
        // a selection.
        setResult(RESULT_CANCELED)
        setContent {
            KeyGoTheme {
                BiometricPromptSupport {
                    val viewModel = koinViewModel<AutofillViewModel>()
                    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                    val dialogVisibility = uiState.associationDialogVisibility
                    val suspicionDialogVisibility = uiState.suspicionDialogVisibility

                    val biometricManager = LocalBiometricManager.current

                    ObserveAsEvents(viewModel.events) {
                        when (it) {
                            AutofillEvent.Abort -> cancel()
                            is AutofillEvent.Fill -> finishWithResult(it.dataset)
                        }
                    }

                    // Request biometric prompt, while the background is transparent (shows the app
                    // behind it)
                    ObserveAsEvents(viewModel.biometricRequests) {
                        when (it) {
                            is BiometricRequest.Class3 -> {
                                val result = biometricManager.authenticate(it)
                                viewModel.onBiometricResult(result)
                            }
                        }
                    }

                    LaunchedEffect(Unit) {
                        viewModel.start()
                    }

                    if (uiState.request !is Request.None) {
                        val navController = rememberNavController()
                        AutofillUi(
                            navController = navController,
                            onItemSelected = { viewModel.onEvent(AutofillUiEvent.OnItemSelected(it)) },
                            onSaved = ::finishWithResult,
                            onAuthenticationSucceeded = {
                                when (uiState.request) {
                                    is Request.JustAuthenticateWithPwd -> viewModel.onEvent(
                                        AutofillUiEvent.OnAuthenticated
                                    )

                                    else -> {
                                        navController.navigate(uiState.request.destination) {
                                            popUpTo<RouteDestination.Auth> { inclusive = true }
                                        }
                                    }
                                }
                            },
                            showBiometricPromptIfPossible = uiState.request !is Request.JustAuthenticateWithPwd
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
                }
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