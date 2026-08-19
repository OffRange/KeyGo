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
import de.davis.keygo.core.util.fold
import de.davis.keygo.core.util.getOrNull
import de.davis.keygo.core.util.onFailure
import de.davis.keygo.feature.credentials.presentation.auth.SessionAuthState
import de.davis.keygo.feature.credentials.presentation.auth.UnlockOutcome
import de.davis.keygo.feature.credentials.presentation.auth.mapUnlockError
import de.davis.keygo.rust.passkey.PasskeyManager
import de.davis.keygo.rust.passkey.getPasskeyInformation
import de.davis.keygo.rust.passkey.registerWithResult
import de.davisalessandro.keygo.rust.PasskeyInformation
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

    private val _excluded = MutableStateFlow(false)
    val excluded = _excluded.asStateFlow()

    private val biometricChannel = Channel<Unit>(Channel.BUFFERED)
    val biometricFlow = biometricChannel.receiveAsFlow()

    private lateinit var pendingRequest: CreatePublicKeyCredentialRequest
    lateinit var passkeyInformation: PasskeyInformation

    private var started = false
    private var associating = false

    fun setRequest(request: CreatePublicKeyCredentialRequest): Boolean {
        pendingRequest = request
        passkeyInformation = passkeyManager.getPasskeyInformation(request.requestJson)
            .getOrNull()
            ?: return false

        start()
        return true
    }

    /**
     * Answers the relying party's exclusion list before any unlock prompt, then routes to the auth
     * path.
     *
     * Credential ids are not secrets and the passkey table is not gated behind the session, so an
     * excluded request can be resolved without making the user authenticate for a registration
     * that can never succeed.
     *
     * Runs once. [setRequest] is called again on every configuration change.
     */
    private fun start() {
        if (started) return
        started = true

        viewModelScope.launch {
            val excludeCredentials = passkeyInformation.excludeCredentials.toSet()
            if (passkeyRepository.doCredentialIdsExist(excludeCredentials))
                return@launch _excluded.update { true }

            val account = accountRepository.getOrNull()
            val biometricUsable = biometricAvailabilityRepository.availability()
                    && account?.biometricWrappedArk != null

            if (biometricUsable) {
                _authState.update { SessionAuthState.TryBiometric }
                biometricChannel.send(Unit)
            } else _authState.update { SessionAuthState.NeedsPassword }
        }
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

    /**
     * Registers the pending request and links the resulting passkey to [itemId].
     *
     * Runs at most once per activity. Every caller is a one-shot UI action, but nothing stops a
     * second tap from landing before the first one has recomposed the dialog away. A repeat run
     * would mint a second key pair and credential id, store both, and answer the relying party with
     * only whichever response wins the race, leaving the other private key in the vault as a
     * credential the site has never heard of.
     *
     * The flag is only ever touched from the main thread, where the UI callbacks run.
     */
    fun associatePasskeyAndFinish(itemId: ItemId) {
        if (associating) return
        associating = true

        viewModelScope.launch {
            val response = passkeyManager.registerWithResult(pendingRequest.requestJson)
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
