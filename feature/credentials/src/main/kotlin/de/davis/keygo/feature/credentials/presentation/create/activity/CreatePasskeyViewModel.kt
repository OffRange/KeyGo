package de.davis.keygo.feature.credentials.presentation.create.activity

import android.util.Log
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.Passkey
import de.davis.keygo.core.item.domain.model.PasskeyUser
import de.davis.keygo.core.item.domain.model.SecretData
import de.davis.keygo.core.item.domain.repository.PasskeyRepository
import de.davis.keygo.core.item.domain.repository.PasswordRepository
import de.davis.keygo.core.security.domain.model.CiphertextData
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
    private val passwordRepository: PasswordRepository,
    private val passkeyManager: PasskeyManager,
) : ViewModel() {

    private val biometricChannel = Channel<CreatePasskeyBiometricRequestEvent>()
    val biometricRequest = biometricChannel.receiveAsFlow()

    private val _event = Channel<CreatePasskeyEvent>()
    val event = _event.receiveAsFlow()

    private var registrationResponse: RegistrationResponse? = null
    private var key: CiphertextData? = null


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

            // Request authentication
            registrationResponse?.let {
                biometricChannel.send(
                    CreatePasskeyBiometricRequestEvent.EncryptPasskeyEncryptionKey(
                        key = it.privateKey
                    )
                )
            }
        }
    }


    fun passkeyEncrypted(key: CiphertextData) {
        this.key = key
        _event.trySend(CreatePasskeyEvent.ShowList)
    }

    fun associatePasskeyAndFinish(itemId: ItemId) {
        viewModelScope.launch {
            val registrationResponse =
                registrationResponse ?: return@launch abort("Response was null")
            val key = key ?: return@launch abort("Key was null")

            val passkey = Passkey(
                credentialId = registrationResponse.credentialId,
                privateKey = SecretData(
                    data = key.bytes,
                    iv = key.iv,
                    decryptedDataType = SecretData.DecryptedDataType.StringType
                ),
                rp = registrationResponse.rp,
                passwordId = itemId,
                user = PasskeyUser(
                    name = registrationResponse.userName,
                    displayName = registrationResponse.userDisplayName
                )
            )

            passkeyRepository.createPasskey(passkey)
            _event.send(CreatePasskeyEvent.Finish(registrationResponse.response))
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