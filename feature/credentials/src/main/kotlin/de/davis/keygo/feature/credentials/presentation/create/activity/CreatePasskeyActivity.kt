package de.davis.keygo.feature.credentials.presentation.create.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.exceptions.CreateCredentialUnknownException
import androidx.credentials.provider.PendingIntentHandler
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import de.davis.keygo.core.security.presentation.rememberBiometricCryptoController
import de.davis.keygo.core.ui.theme.KeyGoTheme
import de.davis.keygo.core.util.onFailure
import de.davis.keygo.core.util.onSuccess
import de.davis.keygo.core.util.presentation.ObserveAsEvents
import de.davis.keygo.feature.list_screen.presentation.ItemListScreen
import de.davis.keygo.feature.list_screen.presentation.NoItemStrategy
import de.davis.keygo.feature.list_screen.presentation.rememberItemListScreenSearchState
import kotlinx.serialization.Serializable
import org.koin.androidx.viewmodel.ext.android.viewModel


@Serializable
private data object ListDest

internal class CreatePasskeyActivity : FragmentActivity() {

    private val viewModel by viewModel<CreatePasskeyViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val request = PendingIntentHandler.retrieveProviderCreateCredentialRequest(intent)
        val callingRequest = request?.callingRequest as? CreatePublicKeyCredentialRequest
            ?: return cancel("Invalid CreatePublicKeyCredentialRequest")

        viewModel.updateCreatePublicKeyCredentialRequest(callingRequest)
        setResult(RESULT_CANCELED)

        setContent {
            KeyGoTheme {
                val navController = rememberNavController()
                val biometricCryptoController = rememberBiometricCryptoController()
                BackHandler { cancel() }

                ObserveAsEvents(flow = viewModel.biometricRequest) {
                    when (it) {
                        is CreatePasskeyBiometricRequestEvent.EncryptPasskeyEncryptionKey -> {
                            biometricCryptoController.requestEncryption(
                                keyId = it.keyId,
                                byteArray = it.key,
                                policy = it.policy
                            ).onSuccess(viewModel::passkeyEncrypted)
                                .onFailure { error -> cancel("Biometric Failed: $error") }
                        }

                        else -> cancel("Unsupported Biometric Request")
                    }
                }

                ObserveAsEvents(flow = viewModel.event) {
                    when (it) {
                        CreatePasskeyEvent.Abort -> cancel()
                        CreatePasskeyEvent.ShowList -> navController.navigate(ListDest) {
                            popUpTo<Unit> { inclusive = true }
                        }

                        is CreatePasskeyEvent.Finish -> finishWithSuccess(it.responseJson)
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = Unit
                ) {
                    composable<Unit> {
                        // Show nothing by default
                    }

                    composable<ListDest> {
                        PasskeyItemListScreen(
                            viewModel = viewModel,
                        )
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun PasskeyItemListScreen(viewModel: CreatePasskeyViewModel) {
        val passwords by viewModel.listItemState.collectAsStateWithLifecycle()
        val searchState = rememberItemListScreenSearchState(
            searcher = viewModel::searcher,
            onQuerySubmitted = viewModel::onSearchSubmit,
        )

        ItemListScreen(
            items = passwords,
            searchState = searchState,
            onDelete = { },
            onItemClick = viewModel::onItemClick,
            onSearchResultClick = viewModel::onItemClick,
            onItemLongClick = { },
            onCreateItemRequest = { },
            enableSwipeToDelete = false,
            notFoundStrategy = NoItemStrategy.ShowMessage,
            dockedSearchResults = false,
        )
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
