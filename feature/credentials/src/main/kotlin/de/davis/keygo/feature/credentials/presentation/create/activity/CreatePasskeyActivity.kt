package de.davis.keygo.feature.credentials.presentation.create.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.exceptions.CreateCredentialUnknownException
import androidx.credentials.provider.PendingIntentHandler
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.security.presentation.rememberBiometricCryptoController
import de.davis.keygo.core.ui.theme.KeyGoTheme
import de.davis.keygo.core.util.onFailure
import de.davis.keygo.core.util.onSuccess
import de.davis.keygo.core.util.presentation.ObserveAsEvents
import de.davis.keygo.feature.credentials.R
import de.davis.keygo.feature.item.create.presentation.login.LoginScreen
import de.davis.keygo.feature.list_screen.presentation.ItemListScreen
import de.davis.keygo.feature.list_screen.presentation.NoItemStrategy
import kotlinx.serialization.Serializable
import org.koin.androidx.viewmodel.ext.android.viewModel


@Serializable
private data object ListDest

@Serializable
private data object CreateItem

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

                var confirmationEvent by rememberSaveable {
                    mutableStateOf<CreatePasskeyEvent.OpenConfirmationDialog?>(null)
                }

                ObserveAsEvents(flow = viewModel.event) {
                    when (it) {
                        CreatePasskeyEvent.Abort -> cancel()
                        CreatePasskeyEvent.ShowList -> navController.navigate(ListDest) {
                            popUpTo<Unit> { inclusive = true }
                        }

                        is CreatePasskeyEvent.Finish -> finishWithSuccess(it.responseJson)

                        is CreatePasskeyEvent.OpenConfirmationDialog -> {
                            confirmationEvent = it
                        }
                    }
                }

                confirmationEvent?.let { event ->
                    AlertDialog(
                        onDismissRequest = { confirmationEvent = null },
                        title = {
                            Text(stringResource(R.string.link_passkey))
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    viewModel.associatePasskeyAndFinish(event.itemId)
                                    confirmationEvent = null
                                }
                            ) {
                                Text(stringResource(R.string.yes))
                            }
                        },
                        text = {
                            Text(
                                stringResource(
                                    R.string.link_passkey_confirmation_message,
                                    event.rp
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    confirmationEvent = null
                                }
                            ) {
                                Text(stringResource(R.string.no))
                            }
                        }
                    )
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
                            onItemClick = viewModel::onItemClicked,
                            onCreateClicked = {
                                navController.navigate(CreateItem)
                            }
                        )
                    }

                    composable<CreateItem> {
                        LoginScreen(
                            loginCreated = {
                                viewModel.associatePasskeyAndFinish(it)
                            },
                            navigateBack = { cancel("User cancelled passkey creation") },
                        )
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun PasskeyItemListScreen(
        onItemClick: (ItemId) -> Unit,
        onCreateClicked: () -> Unit
    ) {
        Scaffold(
            floatingActionButton = {
                FloatingActionButton(
                    onClick = onCreateClicked
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                }
            }
        ) { innerPadding ->
            ItemListScreen(
                onItemClick = onItemClick,
                onItemLongClick = { },
                onCreateItemRequest = { onCreateClicked() },
                enableDeletion = false,
                enableSelection = false,
                modifier = Modifier
                    .consumeWindowInsets(innerPadding)
                    .padding(innerPadding),
                notFoundStrategy = NoItemStrategy.ShowMessage,
                dockedSearchResults = false,
            )
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
