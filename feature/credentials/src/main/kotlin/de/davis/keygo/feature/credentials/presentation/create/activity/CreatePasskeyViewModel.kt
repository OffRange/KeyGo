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
import de.davis.keygo.core.item.domain.repository.ItemRepository
import de.davis.keygo.core.item.domain.repository.PasskeyRepository
import de.davis.keygo.core.security.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.security.domain.crypto.encrypt
import de.davis.keygo.core.security.domain.crypto.model.WrappedItemKeyInformation
import de.davis.keygo.core.security.domain.crypto.model.WrappedVaultKeyInformation
import de.davis.keygo.core.security.domain.repository.BiometricAvailabilityRepository
import de.davis.keygo.core.util.getOrNull
import de.davis.keygo.feature.credentials.presentation.auth.SessionAuthState
import de.davis.keygo.feature.credentials.presentation.auth.UnlockOutcome
import de.davis.keygo.feature.credentials.presentation.auth.mapUnlockError
import de.davis.keygo.rust.passkey.PasskeyManager
import de.davis.keygo.rust.passkey.getExcludedCredentialIds
import de.davis.keygo.rust.passkey.registerWithResult
import de.davisalessandro.keygo.rust.ItemAad
import de.davisalessandro.keygo.rust.RegistrationResponse
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class CreatePasskeyViewModel(
    private val passkeyRepository: PasskeyRepository,
    private val itemRepository: ItemRepository,
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

    private var pendingRequest: CreatePublicKeyCredentialRequest? = null
    private var registrationResponse: RegistrationResponse? = null

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

    fun setRequest(request: CreatePublicKeyCredentialRequest) {
        pendingRequest = request
    }

    fun onUnlocked() {
        if (_authState.value == SessionAuthState.Authenticated) return
        _authState.value = SessionAuthState.Authenticated
        val req = pendingRequest ?: return
        runOperation(req)
    }

    fun onUnlockFailed(error: UnlockError) {
        when (mapUnlockError(error)) {
            UnlockOutcome.Abort -> viewModelScope.launch { abort("biometric $error") }
            UnlockOutcome.NeedsPassword -> _authState.value = SessionAuthState.NeedsPassword
        }
    }

    private fun runOperation(request: CreatePublicKeyCredentialRequest) {
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

            val envelope = itemRepository.getItemKeyEnvelope(itemId)
                ?: return@launch abort("Failed to get item key envelope for id $itemId")

            val encryptedPrivateKey = try {
                cryptographicScopeProvider.itemScope(
                    wrappedVaultKeyInformation = WrappedVaultKeyInformation(
                        wrappedVaultKey = envelope.vaultKeyInformation,
                        vaultId = envelope.vaultId,
                    ),
                    wrappedItemKeyInformation = WrappedItemKeyInformation(
                        itemAad = ItemAad(
                            itemId = envelope.itemId,
                            vaultId = envelope.vaultId,
                        ),
                        wrappedItemKey = envelope.itemKeyInformation
                    )
                ) {
                    Passkey.PrivateKey.encrypt(response.privateKey)
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
