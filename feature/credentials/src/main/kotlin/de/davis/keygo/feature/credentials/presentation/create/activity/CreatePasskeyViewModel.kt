package de.davis.keygo.feature.credentials.presentation.create.activity

import android.util.Log
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.Passkey
import de.davis.keygo.core.item.domain.model.PasskeyUser
import de.davis.keygo.core.item.domain.model.SecretData
import de.davis.keygo.core.item.domain.repository.LoginRepository
import de.davis.keygo.core.item.domain.repository.PasskeyRepository
import de.davis.keygo.core.item.domain.repository.VaultRepository
import de.davis.keygo.core.security.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.security.domain.crypto.model.WrappedVaultKeyInformation
import de.davis.keygo.core.security.domain.crypto.wrappedItemKeyInformation
import de.davis.keygo.core.util.getOrNull
import de.davis.keygo.rust.passkey.PasskeyManager
import de.davis.keygo.rust.passkey.getExcludedCredentialIds
import de.davis.keygo.rust.passkey.registerWithResult
import de.davisalessandro.keygo.rust.RegistrationResponse
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class CreatePasskeyViewModel(
    private val passkeyRepository: PasskeyRepository,
    private val loginRepository: LoginRepository,
    private val vaultRepository: VaultRepository,
    private val cryptographicScopeProvider: CryptographicScopeProvider,
    private val passkeyManager: PasskeyManager,
) : ViewModel() {

    private val _event = Channel<CreatePasskeyEvent>()
    val event = _event.receiveAsFlow()

    private var registrationResponse: RegistrationResponse? = null


    fun updateCreatePublicKeyCredentialRequest(request: CreatePublicKeyCredentialRequest) {
        viewModelScope.launch {
            val idsToExclude =
                passkeyManager.getExcludedCredentialIds(request.requestJson).getOrNull()
                    ?.toSet()
                    ?: return@launch abort("Failed to get excluded IDs")

            val shouldAbort = passkeyRepository.doCredentialIdsExist(idsToExclude)
            if (shouldAbort) return@launch abort("Credential ID already exists")

            registrationResponse = passkeyManager.registerWithResult(request.requestJson)
                .getOrNull() ?: return@launch abort("Failed to register passkey")

            _event.send(CreatePasskeyEvent.ShowList)
        }
    }

    fun associatePasskeyAndFinish(itemId: ItemId) {
        viewModelScope.launch {
            val response = registrationResponse ?: return@launch abort("Response was null")

            val login = loginRepository.getLoginById(itemId)
                ?: return@launch abort("Login not found for id $itemId")
            val vaultKeyInfo = vaultRepository.getKeyInformation(login.vaultId)
                ?: return@launch abort("Vault key information missing for ${login.vaultId}")

            val encryptedPrivateKey = try {
                cryptographicScopeProvider.itemScope(
                    wrappedVaultKeyInformation = WrappedVaultKeyInformation(
                        wrappedVaultKey = vaultKeyInfo,
                        vaultId = login.vaultId,
                    ),
                    wrappedItemKeyInformation = login.wrappedItemKeyInformation(),
                ) {
                    val ct = response.privateKey.encrypt(label = Passkey.LABEL_PRIVATE_KEY)
                    SecretData(
                        data = ct.data,
                        iv = ct.iv,
                        decryptedDataType = SecretData.DecryptedDataType.StringType,
                    )
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to encrypt passkey private key", t)
                return@launch abort("Failed to encrypt passkey private key")
            }

            val passkey = Passkey(
                credentialId = response.credentialId,
                privateKey = encryptedPrivateKey,
                rp = response.rp,
                loginId = itemId,
                user = PasskeyUser(
                    name = response.userName,
                    displayName = response.userDisplayName,
                ),
            )

            passkeyRepository.createPasskey(passkey)
            _event.send(CreatePasskeyEvent.Finish(response.response))
        }
    }

    fun onItemClicked(itemId: ItemId) {
        _event.trySend(
            CreatePasskeyEvent.OpenConfirmationDialog(
                itemId = itemId,
                itemName = "N/A",
                rp = registrationResponse?.rp ?: "N/A"
            )
        )
    }

    private suspend fun abort(msg: String? = null) {
        msg?.let { Log.w(TAG, "Aborting: $it") }
        _event.send(CreatePasskeyEvent.Abort)
    }

    companion object {
        private const val TAG = "CreatePasskeyViewModel"
    }
}
