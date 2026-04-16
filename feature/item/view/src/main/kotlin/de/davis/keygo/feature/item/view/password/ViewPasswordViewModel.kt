package de.davis.keygo.feature.item.view.password

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.crypto.decryptSecretData
import de.davis.keygo.core.item.domain.model.DomainInfo
import de.davis.keygo.core.item.domain.repository.ItemRepository
import de.davis.keygo.core.item.domain.repository.PasswordRepository
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.core.security.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.util.domain.resolver.RegistrableDomainResolver
import de.davis.keygo.core.util.getOrNull
import de.davis.keygo.core.util.onFailure
import de.davis.keygo.core.util.onSuccess
import de.davis.keygo.feature.item.core.domain.model.PasswordError
import de.davis.keygo.feature.item.core.domain.model.UpsertPassword
import de.davis.keygo.feature.item.core.domain.model.fieldUpdate
import de.davis.keygo.feature.item.core.domain.model.onSet
import de.davis.keygo.feature.item.core.domain.model.set
import de.davis.keygo.feature.item.core.domain.usecase.CreateNewOrUpdatePasswordUseCase
import de.davis.keygo.feature.item.core.presentation.model.InputFieldError
import de.davis.keygo.feature.item.core.presentation.model.NavigationEvent
import de.davis.keygo.feature.item.core.presentation.password.model.FieldType
import de.davis.keygo.feature.item.view.domain.WebsiteHandler
import de.davis.keygo.feature.item.view.domain.usecase.IsValidUrlUseCase
import de.davis.keygo.feature.item.view.password.model.ModificationDialog
import de.davis.keygo.feature.item.view.password.model.ViewPasswordState
import de.davis.keygo.feature.item.view.password.model.ViewPasswordUiEvent
import de.davis.keygo.feature.item.view.password.model.asObfuscatedString
import de.davis.keygo.feature.totp.domain.model.TotpInformation
import de.davis.keygo.feature.totp.domain.repository.TotpGenerator
import de.davis.keygo.feature.totp.domain.usecase.GetTotpSecretFromUrlUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class ViewPasswordViewModel(
    private val itemRepository: ItemRepository,
    private val passwordRepository: PasswordRepository,
    private val cryptographicScopeProvider: CryptographicScopeProvider,
    private val updatePassword: CreateNewOrUpdatePasswordUseCase,
    private val isValidUrl: IsValidUrlUseCase,
    private val websiteHandler: WebsiteHandler,
    private val totpGenerator: TotpGenerator,
    private val registrableDomainResolver: RegistrableDomainResolver,
    private val getTotpSecret: GetTotpSecretFromUrlUseCase
) : ViewModel() {

    private val _modificationDialogState = MutableStateFlow<ModificationDialog?>(null)
    private val _scanning = MutableStateFlow(false)
    private val _itemId = MutableStateFlow<ItemId?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val stateWithoutModification = _itemId
        .filterNotNull()
        .distinctUntilChanged()
        .flatMapLatest { id ->
            passwordRepository.observePasswordById(id).filterNotNull().flatMapLatest { password ->
                coroutineScope {
                    val obfuscatedString = async {
                        cryptographicScopeProvider.scope {
                            password.password.decryptSecretData()
                        }.asObfuscatedString()
                    }

                    val totpSecret = password.totpSecret?.let { totpSecret ->
                        async {
                            cryptographicScopeProvider.scope {
                                totpSecret.decryptSecretData().encodeToByteArray()
                            }
                        }
                    }


                    val base = ViewPasswordState(
                        name = password.name,
                        passkeyRPs = password.passkeyRPs,
                        password = obfuscatedString.await(),
                        passwordStrengthScore = password.score,
                        username = password.username.orEmpty(),
                        domains = password.domainInfos,
                        note = password.note.orEmpty(),
                        totpInformation = TotpInformation("", 0, 0),
                        pinned = password.pinned,
                    )

                    when {
                        totpSecret == null -> flowOf(base)
                        else -> totpGenerator.observeTotp(totpSecret.await()).map {
                            base.copy(totpInformation = it)
                        }
                    }
                }
            }
        }.flowOn(Dispatchers.Default)


    val state =
        combine(
            stateWithoutModification,
            _modificationDialogState,
            _scanning
        ) { state, modificationDialog, scanning ->
            state.copy(modificationDialog = modificationDialog, scanning = scanning)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ViewPasswordState()
        )

    private val navigationEventChannel = Channel<NavigationEvent>()
    val navigationEvent = navigationEventChannel.receiveAsFlow()

    fun init(itemId: ItemId) {
        _itemId.update {
            itemId
        }
    }

    fun onEvent(event: ViewPasswordUiEvent) {
        when (event) {
            ViewPasswordUiEvent.OnBackClick -> {
                if (_scanning.value) _scanning.update { false }
                else viewModelScope.launch {
                    navigationEventChannel.send(NavigationEvent.NavigateBack)
                }
            }

            is ViewPasswordUiEvent.OpenWebsite -> {
                val url = event.domain
                if (!isValidUrl(url))
                    return

                websiteHandler.openWebsite(url)
            }

            ViewPasswordUiEvent.OnPinClick -> {
                _itemId.value?.let { id ->
                    viewModelScope.launch {
                        itemRepository.setPinned(id, !state.value.pinned)
                    }
                }
            }

            ViewPasswordUiEvent.OnEditRequest -> {
                _itemId.value?.let { id ->
                    viewModelScope.launch {
                        navigationEventChannel.send(
                            NavigationEvent.NavigateToEdit(
                                VaultItemType.Password,
                                id
                            )
                        )
                    }
                }
            }

            ViewPasswordUiEvent.OnCloseDialog -> {
                _modificationDialogState.update { null }
            }

            is ViewPasswordUiEvent.OnModifyFieldRequest -> {
                val fieldType = event.fieldType
                val state = state.value
                val initialValue = when (fieldType) {
                    FieldType.Name -> state.name
                    FieldType.Password -> state.password.raw
                    FieldType.Totp -> "" // TOTP is not editable in this context
                    FieldType.Username -> state.username
                    FieldType.Domain -> "" // Only allow adding new domains, not editing existing ones
                    FieldType.Note -> state.note
                }

                _modificationDialogState.update {
                    ModificationDialog(
                        fieldType = fieldType,
                        initialValue = initialValue
                    )
                }
            }

            ViewPasswordUiEvent.OnScanCodeRequest -> {
                _modificationDialogState.update { null }
                _scanning.update { true }
            }

            is ViewPasswordUiEvent.OnCodesScanned -> {
                _scanning.update { false }
                val secret = event.codes.firstNotNullOfOrNull {
                    getTotpSecret(it).getOrNull()
                }?.secret ?: return

                _itemId.value?.let { id ->
                    viewModelScope.launch {
                        updatePassword(
                            UpsertPassword.update(
                                vaultId = id,
                                totpSecret = fieldUpdate(secret)
                            )
                        )
                    }
                }
            }

            is ViewPasswordUiEvent.OnSubmitModification -> {
                val dialog = _modificationDialogState.value ?: return
                val newText = fieldUpdate(event.input)

                _itemId.value?.let { id ->
                    viewModelScope.launch {
                        updatePassword(
                            when (dialog.fieldType) {
                                FieldType.Name -> UpsertPassword.update(
                                    vaultId = id,
                                    name = newText
                                )

                                FieldType.Password -> UpsertPassword.update(
                                    vaultId = id,
                                    password = newText
                                )

                                FieldType.Totp -> UpsertPassword.update(
                                    vaultId = id,
                                    totpSecret = newText
                                )

                                FieldType.Username -> UpsertPassword.update(
                                    vaultId = id,
                                    username = newText
                                )

                                FieldType.Domain -> newText.onSet {
                                    val eTLD1 = registrableDomainResolver.resolve(it)
                                    val updatedDomains = state.value.domains + DomainInfo(
                                        id,
                                        it,
                                        eTLD1
                                    )

                                    UpsertPassword.update(
                                        vaultId = id,
                                        domains = set(updatedDomains)
                                    )
                                } ?: return@launch

                                FieldType.Note -> UpsertPassword.update(
                                    vaultId = id,
                                    note = newText
                                )
                            }
                        ).onFailure { failure ->
                            _modificationDialogState.update {
                                dialog.copy(
                                    error = if (failure.contains(PasswordError.BlankPassword)
                                        || failure.contains(PasswordError.BlankName)
                                    ) InputFieldError.Empty else null
                                )
                            }
                        }.onSuccess {
                            _modificationDialogState.update { null }
                        }
                    }
                }
            }
        }
    }
}