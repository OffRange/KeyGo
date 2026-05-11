package de.davis.keygo.feature.item.view.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.DomainInfo
import de.davis.keygo.core.item.domain.repository.ItemRepository
import de.davis.keygo.core.item.domain.repository.VaultRepository
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.core.security.domain.crypto.decrypt
import de.davis.keygo.core.security.domain.usecase.LoginWithCryptoScopeUseCase
import de.davis.keygo.core.util.domain.resolver.RegistrableDomainResolver
import de.davis.keygo.core.util.getOrNull
import de.davis.keygo.core.util.onFailure
import de.davis.keygo.core.util.onSuccess
import de.davis.keygo.feature.item.core.domain.model.LoginError
import de.davis.keygo.feature.item.core.domain.model.UpsertLogin
import de.davis.keygo.feature.item.core.domain.model.fieldUpdate
import de.davis.keygo.feature.item.core.domain.model.onSet
import de.davis.keygo.feature.item.core.domain.model.set
import de.davis.keygo.feature.item.core.domain.usecase.CreateNewOrUpdateLoginUseCase
import de.davis.keygo.feature.item.core.presentation.login.model.FieldType
import de.davis.keygo.feature.item.core.presentation.model.InputFieldError
import de.davis.keygo.feature.item.core.presentation.model.NavigationEvent
import de.davis.keygo.feature.item.view.domain.WebsiteHandler
import de.davis.keygo.feature.item.view.domain.usecase.IsValidUrlUseCase
import de.davis.keygo.feature.item.view.login.model.ModificationDialog
import de.davis.keygo.feature.item.view.login.model.ViewLoginState
import de.davis.keygo.feature.item.view.login.model.ViewLoginUiEvent
import de.davis.keygo.feature.item.view.login.model.asObfuscatedString
import de.davis.keygo.feature.totp.domain.model.TotpValue
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
internal class ViewLoginViewModel(
    private val itemRepository: ItemRepository,
    private val vaultRepository: VaultRepository,
    private val updateLogin: CreateNewOrUpdateLoginUseCase,
    private val isValidUrl: IsValidUrlUseCase,
    private val websiteHandler: WebsiteHandler,
    private val totpGenerator: TotpGenerator,
    private val registrableDomainResolver: RegistrableDomainResolver,
    private val getTotpSecret: GetTotpSecretFromUrlUseCase,
    private val observeLoginWithCryptoScope: LoginWithCryptoScopeUseCase,
) : ViewModel() {

    private val _modificationDialogState = MutableStateFlow<ModificationDialog?>(null)
    private val _scanning = MutableStateFlow(false)
    private val _itemId = MutableStateFlow<ItemId?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val _stateWithoutModification = _itemId
        .filterNotNull()
        .distinctUntilChanged()
        .flatMapLatest { id ->
            observeLoginWithCryptoScope.observe(itemId = id) { login ->
                val (obfuscated, vaultMetadata) = coroutineScope {
                    val obfuscated = async {
                        login.password.decrypt().asObfuscatedString()
                    }
                    val vaultMetadata = async {
                        vaultRepository.getVaultMetadata(login.vaultId)
                    }

                    Pair(
                        obfuscated.await(),
                        vaultMetadata.await(),
                    )
                }

                val base = ViewLoginState(
                    name = login.name,
                    vaultMetadata = vaultMetadata,
                    passkeyRPs = login.passkeyRPs,
                    password = obfuscated,
                    passwordStrengthScore = login.passwordScore,
                    username = login.username.orEmpty(),
                    domains = login.domainInfos,
                    note = login.note.orEmpty(),
                    totpValue = TotpValue("", 0, 0),
                    pinned = login.pinned,
                )

                when (val totp = login.totp) {
                    null -> flowOf(base)
                    else -> totpGenerator.observeTotpCode(totp).map {
                        base.copy(totpValue = it)
                    }
                }
            }
                .filterNotNull()
                .flatMapLatest { it }
        }.flowOn(Dispatchers.Default)


    val state = combine(
        _stateWithoutModification,
        _modificationDialogState,
        _scanning,
    ) { state, modificationDialog, scanning ->
        state.copy(
            modificationDialog = modificationDialog,
            scanning = scanning,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ViewLoginState(),
    )

    private val navigationEventChannel = Channel<NavigationEvent>()
    val navigationEvent = navigationEventChannel.receiveAsFlow()

    fun init(itemId: ItemId) {
        _itemId.update {
            itemId
        }
    }

    fun onEvent(event: ViewLoginUiEvent) {
        when (event) {
            ViewLoginUiEvent.OnBackClick -> {
                if (_scanning.value) _scanning.update { false }
                else viewModelScope.launch {
                    navigationEventChannel.send(NavigationEvent.NavigateBack)
                }
            }

            is ViewLoginUiEvent.OpenWebsite -> {
                val url = event.domain
                if (!isValidUrl(url))
                    return

                websiteHandler.openWebsite(url)
            }

            ViewLoginUiEvent.OnPinClick -> {
                _itemId.value?.let { id ->
                    viewModelScope.launch {
                        itemRepository.setPinned(id, !state.value.pinned)
                    }
                }
            }

            ViewLoginUiEvent.OnEditRequest -> {
                _itemId.value?.let { id ->
                    viewModelScope.launch {
                        navigationEventChannel.send(
                            NavigationEvent.NavigateToEdit(
                                VaultItemType.Login,
                                id,
                            )
                        )
                    }
                }
            }

            ViewLoginUiEvent.OnCloseDialog -> {
                _modificationDialogState.update { null }
            }

            is ViewLoginUiEvent.OnModifyFieldRequest -> {
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
                        initialValue = initialValue,
                    )
                }
            }

            ViewLoginUiEvent.OnScanCodeRequest -> {
                _modificationDialogState.update { null }
                _scanning.update { true }
            }

            is ViewLoginUiEvent.OnCodesScanned -> {
                _scanning.update { false }
                val secret = event.codes.firstNotNullOfOrNull {
                    getTotpSecret(it).getOrNull()
                }?.secret ?: return

                _itemId.value?.let { id ->
                    viewModelScope.launch {
                        updateLogin(
                            UpsertLogin.update(
                                itemId = id,
                                totpSecret = fieldUpdate(secret),
                            )
                        )
                    }
                }
            }

            is ViewLoginUiEvent.OnSubmitModification -> {
                val dialog = _modificationDialogState.value ?: return
                val newText = fieldUpdate(event.input)

                _itemId.value?.let { id ->
                    viewModelScope.launch {
                        updateLogin(
                            when (dialog.fieldType) {
                                FieldType.Name -> UpsertLogin.update(
                                    itemId = id,
                                    name = newText,
                                )

                                FieldType.Password -> UpsertLogin.update(
                                    itemId = id,
                                    password = newText,
                                )

                                FieldType.Totp -> UpsertLogin.update(
                                    itemId = id,
                                    totpSecret = newText,
                                )

                                FieldType.Username -> UpsertLogin.update(
                                    itemId = id,
                                    username = newText,
                                )

                                FieldType.Domain -> newText.onSet {
                                    val eTLD1 = registrableDomainResolver.resolve(it)
                                    val updatedDomains = state.value.domains + DomainInfo(
                                        id,
                                        it,
                                        eTLD1,
                                    )

                                    UpsertLogin.update(
                                        itemId = id,
                                        domains = set(updatedDomains),
                                    )
                                } ?: return@launch

                                FieldType.Note -> UpsertLogin.update(
                                    itemId = id,
                                    note = newText,
                                )
                            }
                        ).onFailure { failure ->
                            _modificationDialogState.update {
                                dialog.copy(
                                    error = if (failure.contains(LoginError.BlankPassword)
                                        || failure.contains(LoginError.BlankName)
                                    ) InputFieldError.Empty else null,
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
