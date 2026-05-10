package de.davis.keygo.feature.credentials.presentation.provide.activity

import android.util.Log
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.identity.domain.model.UnlockError
import de.davis.keygo.core.identity.domain.repository.AccountRepository
import de.davis.keygo.core.item.domain.model.Passkey
import de.davis.keygo.core.item.domain.repository.LoginRepository
import de.davis.keygo.core.item.domain.repository.PasskeyRepository
import de.davis.keygo.core.item.domain.repository.VaultRepository
import de.davis.keygo.core.security.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.security.domain.crypto.model.CryptographicData
import de.davis.keygo.core.security.domain.crypto.model.WrappedVaultKeyInformation
import de.davis.keygo.core.security.domain.crypto.wrappedItemKeyInformation
import de.davis.keygo.core.security.domain.repository.BiometricAvailabilityRepository
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
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class ProvidePasskeyViewModel(
    private val passkeyRepository: PasskeyRepository,
    private val loginRepository: LoginRepository,
    private val vaultRepository: VaultRepository,
    private val cryptographicScopeProvider: CryptographicScopeProvider,
    private val passkeyManager: PasskeyManager,
    private val accountRepository: AccountRepository,
    private val biometricAvailabilityRepository: BiometricAvailabilityRepository,
) : ViewModel() {

    private val _event = Channel<ProvidePasskeyEvent>(Channel.BUFFERED)
    val event = _event.receiveAsFlow()

    private val _authState = MutableStateFlow<SessionAuthState>(SessionAuthState.TryBiometric)
    val authState = _authState.asStateFlow()

    private val biometricChannel = Channel<Unit>(Channel.BUFFERED)
    val biometricFlow = biometricChannel.receiveAsFlow()

    private data class PendingRequest(
        val option: GetPublicKeyCredentialOption,
        val credentialId: ByteArray,
    )

    private var pendingRequest: PendingRequest? = null

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

    fun setRequest(option: GetPublicKeyCredentialOption, credentialId: ByteArray) {
        pendingRequest = PendingRequest(option, credentialId)
    }

    fun onUnlocked() {
        _authState.value = SessionAuthState.Authenticated
        val req = pendingRequest ?: return
        runOperation(req)
    }

    fun onUnlockFailed(error: UnlockError) {
        when (mapUnlockError(error)) {
            UnlockOutcome.Abort -> viewModelScope.launch { abort() }
            UnlockOutcome.NeedsPassword -> _authState.value = SessionAuthState.NeedsPassword
        }
    }

    private fun runOperation(req: PendingRequest) {
        viewModelScope.launch {
            val clientDataHash = req.option.clientDataHash
                ?: return@launch abort("ClientDataHash was null")

            val passkey = passkeyRepository.getPasskey(req.credentialId)
                ?: return@launch abort("No passkey found!")

            val login = loginRepository.getLoginById(passkey.loginId)
                ?: return@launch abort("Parent login not found for passkey")
            val vaultKeyInfo = vaultRepository.getKeyInformation(login.vaultId)
                ?: return@launch abort("Vault key information missing for ${login.vaultId}")

            val privateKey = try {
                cryptographicScopeProvider.itemScope(
                    wrappedVaultKeyInformation = WrappedVaultKeyInformation(
                        wrappedVaultKey = vaultKeyInfo,
                        vaultId = login.vaultId,
                    ),
                    wrappedItemKeyInformation = login.wrappedItemKeyInformation(),
                ) {
                    CryptographicData(
                        data = passkey.privateKey.data,
                        iv = passkey.privateKey.iv,
                    ).decrypt(label = Passkey.LABEL_PRIVATE_KEY)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to decrypt passkey private key", t)
                return@launch abort("Failed to decrypt passkey private key")
            }

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
