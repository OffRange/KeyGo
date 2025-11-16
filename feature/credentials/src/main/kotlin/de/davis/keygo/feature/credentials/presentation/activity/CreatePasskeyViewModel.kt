package de.davis.keygo.feature.credentials.presentation.activity

import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.item.domain.repository.PasskeyRepository
import de.davis.keygo.core.util.getOrNull
import de.davis.keygo.rust.passkey.PasskeyManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import java.security.Key

@KoinViewModel
internal class CreatePasskeyViewModel(
    private val passkeyRepository: PasskeyRepository
) : ViewModel() {

    private val biometricChannel = Channel<CreatePasskeyBiometricRequestEvent>()
    val biometricRequest = biometricChannel.receiveAsFlow()

    private val _event = Channel<CreatePasskeyEvent>()
    val event = _event.receiveAsFlow()

    lateinit var request: CreatePublicKeyCredentialRequest

    fun updateCreatePublicKeyCredentialRequest(request: CreatePublicKeyCredentialRequest) {
        this.request = request
        viewModelScope.launch {
            val idsToExclude =
                PasskeyManager.getExcludedCredentialIds(request.requestJson).getOrNull()
                    ?.toSet()
                    ?: return@launch abort()

            val shouldAbort = passkeyRepository.doesCredentialIdsExist(idsToExclude)
            if (shouldAbort) return@launch abort()

            biometricChannel.send(
                CreatePasskeyBiometricRequestEvent.UnwrapPasskeyEncryptionKey(
                    wrappedKey = byteArrayOf(),
                )
            )
        }
    }

    fun passkeyEncryptionKeyUnwrapped(unwrappedKey: Key) {
        // TODO
    }

    private suspend fun abort() {
        _event.send(CreatePasskeyEvent.Abort)
    }
}