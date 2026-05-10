package de.davis.keygo.feature.credentials.presentation.provide.activity

import android.util.Log
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.item.domain.model.Passkey
import de.davis.keygo.core.item.domain.repository.LoginRepository
import de.davis.keygo.core.item.domain.repository.PasskeyRepository
import de.davis.keygo.core.item.domain.repository.VaultRepository
import de.davis.keygo.core.security.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.security.domain.crypto.model.CryptographicData
import de.davis.keygo.core.security.domain.crypto.model.WrappedVaultKeyInformation
import de.davis.keygo.core.security.domain.crypto.wrappedItemKeyInformation
import de.davis.keygo.core.util.onFailure
import de.davis.keygo.core.util.onSuccess
import de.davis.keygo.rust.passkey.PasskeyManager
import de.davis.keygo.rust.passkey.authenticateWithResult
import kotlinx.coroutines.channels.Channel
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
) : ViewModel() {

    private val _event = Channel<ProvidePasskeyEvent>()
    val event = _event.receiveAsFlow()

    fun processGetPublicKeyCredentialOption(
        option: GetPublicKeyCredentialOption,
        credentialId: ByteArray,
    ) {
        viewModelScope.launch {
            val clientDataHash = option.clientDataHash
                ?: return@launch abort("ClientDataHash was null")

            val passkey = passkeyRepository.getPasskey(credentialId)
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
                requestJson = option.requestJson,
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
