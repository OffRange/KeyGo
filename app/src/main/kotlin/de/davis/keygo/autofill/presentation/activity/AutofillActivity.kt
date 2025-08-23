package de.davis.keygo.autofill.presentation.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.autofill.Dataset
import android.view.autofill.AutofillManager
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import de.davis.keygo.R
import de.davis.keygo.autofill.presentation.model.AutofillEvent
import de.davis.keygo.autofill.presentation.model.AutofillInformation
import de.davis.keygo.autofill.presentation.model.Extraction
import de.davis.keygo.core.domain.alias.ItemId
import de.davis.keygo.core.presentation.ObserveAsEvents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import org.koin.androidx.compose.koinViewModel


// Just a prove of concept.
// We are able to launch a activity that is transparent and does not show up in the recent apps list.
// This is useful for UX. We can provide a simple biometric auth prompt just over the current app.
// The user does not notice that the app is switching to a different activity and it feels more
// integrated. This can also be taken further and we can show warning dialogs right on-top of the
// current app.

/**
 * This activity is transparent and does not show up in the recent apps list. It is used to gather
 * the right data that should be used for autofill. If needed this activity can show a dialog or
 * request biometrics to authenticate the user.
 *
 * The transparency allows us to have a more integrated user experience, as the user doesn't see a
 * dedicated UI just to authenticate themself or to show a dialog.
 */
class AutofillActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val viewModel = koinViewModel<AutofillViewModel>()

            ObserveAsEvents(viewModel.events) {
                when (it) {
                    AutofillEvent.Abort -> cancel()

                    is AutofillEvent.Fill -> finishWithResult(it.dataset)
                }
            }
        }
    }

    private fun cancel() {
        setResult(RESULT_CANCELED)
        finish()
    }

    private fun finishWithResult(dataset: Dataset) {
        setResult(
            RESULT_OK,
            Intent().apply {
                putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset)
            }
        )
        finish()
    }

    companion object {

        fun newIntent(context: Context, extraction: Extraction, vaultId: ItemId): Intent = Intent(
            context,
            AutofillActivity::class.java
        ).apply {
            putExtra(
                AutofillViewModel.KEY_AUTOFILL_INFORMATION,
                AutofillInformation(extraction, vaultId)
            )
        }
    }
}


// How about using some thing like a LocalComposition here? And the nearest handles the request.
// The problem we have with a dialog style way is the following:
// The caller can request and dismiss the prompt using a simple boolean state. However, when the
// prompt is closed by the system (e.g., user cancels, fails, ...), the caller *should* reset the
// state back to `false`. So there is no direct control over the dialog.

// The proposed solution would integrate a local manager, just like snackbars
@Composable
fun Test(
    onSuccess: () -> Unit,
    onError: () -> Unit,
    onFailed: () -> Unit
) {
    val fragmentActivity = LocalActivity.current as? FragmentActivity
    requireNotNull(fragmentActivity) { "BiometricAuthHandler must be used within a FragmentActivity context." }

    val prompt = remember(fragmentActivity) {
        BiometricPrompt(
            fragmentActivity,
            Dispatchers.Default.asExecutor(),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError()
                }

                override fun onAuthenticationFailed() {
                    onFailed()
                }
            }
        )
    }

    val title = stringResource(R.string.authenticate)
    val negativeButtonText = stringResource(R.string.cancel)

    DisposableEffect(prompt) {
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setNegativeButtonText(negativeButtonText)
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build(),
        )

        onDispose {
            prompt.cancelAuthentication()
        }
    }
}