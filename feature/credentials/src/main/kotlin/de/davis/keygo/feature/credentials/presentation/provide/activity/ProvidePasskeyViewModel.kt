package de.davis.keygo.feature.credentials.presentation.provide.activity

import android.util.Log
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.item.domain.repository.PasskeyRepository
import de.davis.keygo.core.security.domain.model.CiphertextData
import de.davis.keygo.core.util.onFailure
import de.davis.keygo.core.util.onSuccess
import de.davis.keygo.rust.passkey.PasskeyManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
internal class ProvidePasskeyViewModel(
    private val passkeyRepository: PasskeyRepository
) : ViewModel() {

    private val biometricChannel = Channel<DecryptPasskeyEncryptionKeyRequest>()
    val biometricRequest = biometricChannel.receiveAsFlow()


    private val _event = Channel<ProvidePasskeyEvent>()
    val event = _event.receiveAsFlow()

    private var requestJson: String? = null
    private var clientHashData: ByteArray? = null

    fun updateGetPublicKeyCredentialOption(
        option: GetPublicKeyCredentialOption,
        credentialId: ByteArray
    ) {
        requestJson = option.requestJson
        clientHashData = option.clientDataHash

        viewModelScope.launch {
            val passkey = passkeyRepository.getPasskey(credentialId)
                ?: return@launch abort("No passkey found!")

            biometricChannel.send(
                DecryptPasskeyEncryptionKeyRequest(
                    ciphertextData = CiphertextData(
                        bytes = passkey.privateKey.data,
                        iv = passkey.privateKey.iv,
                    )
                )
            )
        }
    }

    fun onPasskeyDecrypted(key: ByteArray) {
        viewModelScope.launch {
            val requestJson = requestJson ?: return@launch abort("Request was null")
            val clientHashData = clientHashData ?: return@launch abort("ClientHashData was null")
            PasskeyManager.authenticate(
                requestJson = requestJson,
                passkey = key,
                clientDataHash = clientHashData
            ).onFailure {
                Log.w(TAG, "Error during passkey authentication", it)
                abort()
            }.onSuccess {
                _event.send(ProvidePasskeyEvent.Finish(it))
            }
        }
    }

    private suspend fun abort(msg: String? = null) {
        msg?.let { Log.w(TAG, "Aborting: $it") }
        _event.send(ProvidePasskeyEvent.Abort)
    }

    companion object {
        private const val TAG = "ProvidePasskeyViewModel"
    }
}
