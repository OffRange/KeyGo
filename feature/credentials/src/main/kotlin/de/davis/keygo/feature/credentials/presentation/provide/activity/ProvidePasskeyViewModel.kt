package de.davis.keygo.feature.credentials.presentation.provide.activity

import android.util.Log
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.biometrics.domain.model.BiometricPolicy
import de.davis.keygo.core.biometrics.domain.model.BiometricString
import de.davis.keygo.core.identity.domain.model.UnlockableByBiometricsResult
import de.davis.keygo.core.identity.domain.usecase.UnlockWithBiometricsUseCase
import de.davis.keygo.core.identity.domain.usecase.UnlockableByBiometricsUseCase
import de.davis.keygo.core.item.domain.repository.PasskeyRepository
import de.davis.keygo.core.security.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.security.domain.crypto.decrypt
import de.davis.keygo.core.util.fold
import de.davis.keygo.core.util.onFailure
import de.davis.keygo.core.util.onSuccess
import de.davis.keygo.feature.credentials.presentation.auth.SessionAuthState
import de.davis.keygo.feature.credentials.presentation.auth.UnlockOutcome
import de.davis.keygo.feature.credentials.presentation.auth.mapUnlockError
import de.davis.keygo.rust.passkey.PasskeyManager
import de.davis.keygo.rust.passkey.authenticateWithResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class ProvidePasskeyViewModel(
    private val passkeyRepository: PasskeyRepository,
    private val cryptographicScopeProvider: CryptographicScopeProvider,
    private val passkeyManager: PasskeyManager,
    private val unlockableByBiometrics: UnlockableByBiometricsUseCase,
    private val unlockWithBiometrics: UnlockWithBiometricsUseCase,
) : ViewModel() {

    private val _event = Channel<ProvidePasskeyEvent>(Channel.BUFFERED)
    val event = _event.receiveAsFlow()

    private val _authState = MutableStateFlow<SessionAuthState>(SessionAuthState.TryBiometric)
    val authState = _authState.asStateFlow()

    private data class PendingRequest(
        val option: GetPublicKeyCredentialOption,
        val credentialId: ByteArray,
    )

    private lateinit var pendingRequest: PendingRequest

    init {
        viewModelScope.launch {
            val biometricUsable = unlockableByBiometrics() == UnlockableByBiometricsResult.Available
            if (!biometricUsable) return@launch _authState.update { SessionAuthState.NeedsPassword }

            _authState.update { SessionAuthState.TryBiometric }
            unlockWithBiometrics(
                policy = BiometricPolicy(
                    title = BiometricString.Title.Authenticate,
                    negativeButton = BiometricString.NegativeButton.Password,
                )
            ).onSuccess {
                onUnlocked()
            }.onFailure {
                when (mapUnlockError(it)) {
                    UnlockOutcome.Abort -> viewModelScope.launch { abort("biometric: $it") }
                    UnlockOutcome.NeedsPassword -> _authState.update { SessionAuthState.NeedsPassword }
                }
            }
        }
    }

    fun setRequest(option: GetPublicKeyCredentialOption, credentialId: ByteArray) {
        pendingRequest = PendingRequest(option, credentialId)
    }

    fun onUnlocked() {
        _authState.update { SessionAuthState.Authenticated }
        runOperation(pendingRequest)
    }

    private fun runOperation(req: PendingRequest) {
        viewModelScope.launch {
            val clientDataHash = req.option.clientDataHash
                ?: return@launch abort("ClientDataHash was null")

            val passkey = passkeyRepository.getPasskey(req.credentialId)
                ?: return@launch abort("No passkey found!")

            val privateKey = cryptographicScopeProvider.itemScope(itemId = passkey.loginId) {
                passkey.privateKey.decrypt()
            }.fold(
                onSuccess = { it },
                onFailure = { return@launch abort("Failed to decrypt passkey private key: $it") }
            )

            passkeyManager.authenticateWithResult(
                requestJson = req.option.requestJson,
                passkey = privateKey,
                clientDataHash = clientDataHash,
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
