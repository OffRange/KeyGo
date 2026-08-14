package de.davis.keygo.feature.credentials.presentation.create.activity

import android.util.Log
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.identity.domain.model.UnlockError
import de.davis.keygo.core.identity.domain.repository.AccountRepository
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.Passkey
import de.davis.keygo.core.item.domain.model.PasskeyUser
import de.davis.keygo.core.item.domain.repository.PasskeyRepository
import de.davis.keygo.core.security.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.security.domain.crypto.encrypt
import de.davis.keygo.core.security.domain.repository.BiometricAvailabilityRepository
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.fold
import de.davis.keygo.core.util.getOrNull
import de.davis.keygo.core.util.mapFailure
import de.davis.keygo.core.util.onFailure
import de.davis.keygo.feature.credentials.presentation.auth.SessionAuthState
import de.davis.keygo.feature.credentials.presentation.auth.UnlockOutcome
import de.davis.keygo.feature.credentials.presentation.auth.mapUnlockError
import de.davis.keygo.rust.passkey.PasskeyManager
import de.davis.keygo.rust.passkey.getPasskeyInformation
import de.davis.keygo.rust.passkey.registerWithResult
import de.davisalessandro.keygo.rust.PasskeyException
import de.davisalessandro.keygo.rust.PasskeyInformation
import de.davisalessandro.keygo.rust.RegistrationResponse
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class CreatePasskeyViewModel(
    private val passkeyRepository: PasskeyRepository,
    private val cryptographicScopeProvider: CryptographicScopeProvider,
    private val passkeyManager: PasskeyManager,
    private val accountRepository: AccountRepository,
    private val biometricAvailabilityRepository: BiometricAvailabilityRepository,
) : ViewModel() {

    private val _event = Channel<CreatePasskeyEvent>(Channel.BUFFERED)
    val event = _event.receiveAsFlow()

    private val _authState = MutableStateFlow<SessionAuthState>(SessionAuthState.TryBiometric)
    val authState = _authState.asStateFlow()

    private val biometricChannel = Channel<Unit>(Channel.BUFFERED)
    val biometricFlow = biometricChannel.receiveAsFlow()

    private lateinit var pendingRequest: CreatePublicKeyCredentialRequest
    lateinit var passkeyInformation: PasskeyInformation

    init {
        viewModelScope.launch {
            val account = accountRepository.getOrNull()
            val biometricUsable = biometricAvailabilityRepository.availability()
                    && account?.biometricWrappedArk != null

            if (biometricUsable) {
                _authState.value = SessionAuthState.TryBiometric
                biometricChannel.send(Unit)
            } else
                _authState.value = SessionAuthState.NeedsPassword
        }
    }

    fun setRequest(request: CreatePublicKeyCredentialRequest): Boolean {
        pendingRequest = request
        passkeyInformation = passkeyManager.getPasskeyInformation(request.requestJson)
            .getOrNull()
            ?: return false

        return true
    }

    fun onUnlocked() {
        if (_authState.value == SessionAuthState.Authenticated) return
        _authState.update { SessionAuthState.Authenticated }
    }

    fun onUnlockFailed(error: UnlockError) {
        when (mapUnlockError(error)) {
            UnlockOutcome.Abort -> viewModelScope.launch { abort("biometric $error") }
            UnlockOutcome.NeedsPassword -> _authState.update { SessionAuthState.NeedsPassword }
        }
    }

    private suspend fun registerPasskey(request: CreatePublicKeyCredentialRequest): Result<RegistrationResponse, PasskeyCreationError> {
        val shouldAbort = passkeyRepository.doCredentialIdsExist(passkeyInformation.excludeCredentials.toSet())
        if (shouldAbort) return Result.Failure(PasskeyCreationError.Excluded)

        return passkeyManager.registerWithResult(request.requestJson)
            .mapFailure(PasskeyCreationError::RegistrationFailed)
    }

    fun associatePasskeyAndFinish(itemId: ItemId) {
        viewModelScope.launch {
            val response = registerPasskey(pendingRequest)
                .onFailure {
                    Log.d(TAG, "Failed to register passkey: $it")
                }.getOrNull()
                ?: return@launch abort("Failed to register passkey")

            val encryptedPrivateKey = cryptographicScopeProvider.itemScope(itemId = itemId) {
                Passkey.PrivateKey.encrypt(response.privateKey)
            }.fold(
                onSuccess = { it },
                onFailure = { return@launch abort("Failed to encrypt passkey private key: $it") }
            )

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
                rp = passkeyInformation.rp
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

internal sealed interface PasskeyCreationError {
    data class RegistrationFailed(val exception: PasskeyException) : PasskeyCreationError
    data object Excluded : PasskeyCreationError
}
