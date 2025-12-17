package de.davis.keygo.feature.credentials.presentation.activity

import android.util.Log
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.Passkey
import de.davis.keygo.core.item.domain.model.PasskeyUser
import de.davis.keygo.core.item.domain.model.SecretData
import de.davis.keygo.core.item.domain.model.lite.LiteItem
import de.davis.keygo.core.item.domain.repository.PasskeyRepository
import de.davis.keygo.core.item.domain.repository.PasswordRepository
import de.davis.keygo.core.security.domain.model.CiphertextData
import de.davis.keygo.core.util.getOrNull
import de.davis.keygo.rust.passkey.PasskeyManager
import de.davis.keygo.rust.passkey.model.KeyGoRegistrationResponse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
internal class CreatePasskeyViewModel(
    private val passkeyRepository: PasskeyRepository,
    private val passwordRepository: PasswordRepository,
) : ViewModel() {

    private val biometricChannel = Channel<CreatePasskeyBiometricRequestEvent>()
    val biometricRequest = biometricChannel.receiveAsFlow()

    private val _event = Channel<CreatePasskeyEvent>()
    val event = _event.receiveAsFlow()

    private val allItems = passwordRepository.observeLitePasswords()

    private val submittedSearchQuery = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    private val itemSource = submittedSearchQuery.flatMapLatest(::queryToItems)

    val listItemState = itemSource.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    private var registrationResponse: KeyGoRegistrationResponse? = null
    private var key: CiphertextData? = null


    fun updateCreatePublicKeyCredentialRequest(request: CreatePublicKeyCredentialRequest) {
        viewModelScope.launch {
            val idsToExclude =
                PasskeyManager.getExcludedCredentialIds(request.requestJson).getOrNull()
                    ?.toSet()
                    ?: return@launch abort("Failed to get excluded IDs")

            val shouldAbort = passkeyRepository.doCredentialIdsExist(idsToExclude)
            if (shouldAbort) return@launch abort("Credential ID already exists")

            registrationResponse = PasskeyManager.register(request.requestJson)
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


    private suspend fun queryToItems(query: String): Flow<List<LiteItem>> =
        if (query.isBlank()) allItems
        else flowOf(passwordRepository.searchPasswordItem(query))

    fun onSearchSubmit(query: String) {
        submittedSearchQuery.value = query
    }

    suspend fun searcher(query: String): List<LiteItem> = queryToItems(query).first()

    fun passkeyEncrypted(key: CiphertextData) {
        this.key = key
        _event.trySend(CreatePasskeyEvent.ShowList)
    }

    fun onItemClick(itemId: ItemId) {
        viewModelScope.launch {
            val registrationResponse =
                registrationResponse ?: return@launch abort("Response was null")
            val key = key ?: return@launch abort("Key was null")

            val passwordId = passwordRepository.getPasswordIdByVaultId(itemId)
                ?: return@launch abort("No password found for vault ID")

            val passkey = Passkey(
                credentialId = registrationResponse.credentialId,
                privateKey = SecretData(
                    data = key.bytes,
                    iv = key.iv,
                    decryptedDataType = SecretData.DecryptedDataType.StringType
                ),
                rp = registrationResponse.rpId,
                passwordId = passwordId,
                user = PasskeyUser(
                    name = registrationResponse.userName,
                    displayName = registrationResponse.userDisplayName
                )
            )

            passkeyRepository.createPasskey(passkey)
            _event.send(CreatePasskeyEvent.Finish(registrationResponse.responseJson))
        }
    }

    private suspend fun abort(msg: String? = null) {
        msg?.let { Log.w(TAG, "Aborting: $it") }
        _event.send(CreatePasskeyEvent.Abort)
    }

    companion object {
        private const val TAG = "CreatePasskeyViewModel"
    }
}