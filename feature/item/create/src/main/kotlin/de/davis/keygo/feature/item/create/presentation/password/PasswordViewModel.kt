package de.davis.keygo.feature.item.create.presentation.password

import android.util.Log
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.estimator.PasswordStrengthEstimator
import de.davis.keygo.core.item.domain.model.DomainInfo
import de.davis.keygo.core.item.domain.model.Login
import de.davis.keygo.core.item.domain.repository.ItemRepository
import de.davis.keygo.core.item.domain.repository.PasswordRepository
import de.davis.keygo.core.item.domain.repository.VaultContextRepository
import de.davis.keygo.core.item.domain.repository.VaultRepository
import de.davis.keygo.core.security.domain.crypto.decryptSecretData
import de.davis.keygo.core.security.domain.usecase.PasswordWithCryptoScopeUseCase
import de.davis.keygo.core.util.domain.model.snackbar.SnackbarMessage
import de.davis.keygo.core.util.domain.resolver.RegistrableDomainResolver
import de.davis.keygo.core.util.domain.snackbar.SnackbarManager
import de.davis.keygo.core.util.getOrNull
import de.davis.keygo.core.util.onFailure
import de.davis.keygo.core.util.onSuccess
import de.davis.keygo.core.util.presentation.UIText.Companion.ResourceString
import de.davis.keygo.feature.item.core.domain.model.PasswordError
import de.davis.keygo.feature.item.core.domain.model.UpsertPassword
import de.davis.keygo.feature.item.core.domain.model.fieldUpdate
import de.davis.keygo.feature.item.core.domain.model.set
import de.davis.keygo.feature.item.core.domain.usecase.CreateNewOrUpdatePasswordUseCase
import de.davis.keygo.feature.item.core.presentation.model.DetailPaneInformation
import de.davis.keygo.feature.item.core.presentation.model.InputFieldError
import de.davis.keygo.feature.item.core.presentation.password.model.FieldType
import de.davis.keygo.feature.item.create.R
import de.davis.keygo.feature.item.create.presentation.model.VaultsState
import de.davis.keygo.feature.item.create.presentation.password.model.DialogState
import de.davis.keygo.feature.item.create.presentation.password.model.OverrideTotpField
import de.davis.keygo.feature.item.create.presentation.password.model.PasswordBaseState
import de.davis.keygo.feature.item.create.presentation.password.model.PasswordUiEvent
import de.davis.keygo.feature.item.create.presentation.password.model.PasswordUiState
import de.davis.keygo.feature.totp.domain.model.TotpSecretInformation
import de.davis.keygo.feature.totp.domain.usecase.GetTotpSecretFromUrlUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import kotlin.time.Duration.Companion.milliseconds

@KoinViewModel
internal class PasswordViewModel(
    private val passwordWithCryptoScopeUseCase: PasswordWithCryptoScopeUseCase,
    private val itemRepository: ItemRepository,
    private val passwordRepository: PasswordRepository,
    private val vaultContextRepository: VaultContextRepository,
    private val passwordStrengthEstimator: PasswordStrengthEstimator,
    private val createNewOrUpdatePassword: CreateNewOrUpdatePasswordUseCase,
    private val snackbarManager: SnackbarManager,
    private val getTotpSecret: GetTotpSecretFromUrlUseCase,
    private val registrableDomainResolver: RegistrableDomainResolver,
    vaultRepository: VaultRepository
) : ViewModel() {

    private val nameTextFieldState = TextFieldState()
    private val passwordTextFieldState = TextFieldState()
    private val _base = MutableStateFlow(
        PasswordBaseState(
            nameTextFieldState = nameTextFieldState,
            passwordTextFieldState = passwordTextFieldState
        )
    )

    private val _selectedVaultId = MutableStateFlow<VaultId?>(null)

    private val vaultsFlow: Flow<VaultsState> = combine(
        vaultRepository.observeAllVaultMetadata(),
        _selectedVaultId.filterNotNull(),
    ) { metadata, selected ->
        VaultsState(vaults = metadata, selectedVaultId = selected)
    }.distinctUntilChanged()

    val state = combine(_base, vaultsFlow) { base, vaults ->
        PasswordUiState.Ready(base = base, vaultsState = vaults)
    }.onStart {
        observeNameTextField()
        observePasswordTextField()
        primeActiveVaultId()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PasswordUiState.Loading,
    )

    private val itemCreatedEventChannel = Channel<ItemId?>()
    val itemCreatedEvent = itemCreatedEventChannel.receiveAsFlow()

    private var itemId: ItemId? = null
    private var totpSecretInformation: TotpSecretInformation? = null

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun observeNameTextField() {
        snapshotFlow { nameTextFieldState.text }
            .debounce(150.milliseconds)
            .combine(_selectedVaultId.filterNotNull()) { input, vaultId ->
                itemRepository.doesNameExist(
                    input.toString(),
                    excludeId = itemId,
                    vaultId = vaultId
                )
            }
            .distinctUntilChanged()
            .onEach { exists ->
                _base.update {
                    it.copy(nameExists = exists)
                }
            }
            .flowOn(Dispatchers.Default)
            .launchIn(viewModelScope)
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun observePasswordTextField() {
        snapshotFlow { passwordTextFieldState.text }
            .debounce(150.milliseconds)
            .mapLatest { passwordStrengthEstimator(it.toString()) }
            .distinctUntilChanged()
            .onEach { score ->
                _base.update {
                    it.copy(strengthScore = score)
                }
            }
            .flowOn(Dispatchers.Default)
            .launchIn(viewModelScope)
    }

    private fun primeActiveVaultId() {
        viewModelScope.launch {
            val activeId = vaultContextRepository.getLastInteractedVaultId() ?: return@launch
            _selectedVaultId.compareAndSet(null, activeId)
        }
    }

    private fun navigateUp(itemId: ItemId? = null) {
        viewModelScope.launch {
            itemCreatedEventChannel.send(itemId)
        }
    }

    fun init(information: DetailPaneInformation) {
        when (information) {
            is DetailPaneInformation.Init.Existing -> viewModelScope.launch { initWithId(information.id) }
            is DetailPaneInformation.Init.TOTP -> initWithTotpUri(information.uri)
            is DetailPaneInformation.Init.New -> {} // Don't init anything

            is DetailPaneInformation.CreateRaw -> initWithRawItem(information)
        }
    }

    private fun initWithRawItem(createRaw: DetailPaneInformation.CreateRaw) {
        nameTextFieldState.setTextAndPlaceCursorAtEnd(createRaw.name)

        when (createRaw) {
            is DetailPaneInformation.CreateRaw.Password -> {
                val domainInfo = createRaw.url?.let {
                    val eTLD1 = registrableDomainResolver.resolve(it)
                    DomainInfo(
                        value = it,
                        eTLD1 = eTLD1
                    )
                }

                passwordTextFieldState.setTextAndPlaceCursorAtEnd(createRaw.password)
                _base.update {
                    it.copy(
                        usernameTextFieldState = TextFieldState(createRaw.username),
                        domains = setOfNotNull(domainInfo),
                        updating = false
                    )
                }
            }
        }
    }

    private suspend fun initWithId(itemId: ItemId) {
        this.itemId = itemId

        passwordWithCryptoScopeUseCase.oneShot(
            itemId = itemId,
        ) { password ->
            val decrypted = coroutineScope {
                val pwdDeferred =
                    async { password.password.decryptSecretData(label = Login.LABEL_PASSWORD) }
                val totpDeferred = password.totp?.let { totp ->
                    async { totp.secret.decryptSecretData(label = Login.LABEL_TOTP_SECRET) }
                }
                pwdDeferred.await() to totpDeferred?.await()
            }

            nameTextFieldState.setTextAndPlaceCursorAtEnd(password.name)
            passwordTextFieldState.setTextAndPlaceCursorAtEnd(decrypted.first)

            _selectedVaultId.update { password.vaultId }
            _base.update {
                it.copy(
                    totpTextFieldState = TextFieldState(decrypted.second ?: ""),
                    usernameTextFieldState = TextFieldState(password.username ?: ""),
                    domains = password.domainInfos,
                    notesTextFieldState = TextFieldState(password.note ?: ""),
                    dialogState = DialogState.None,
                    updating = true,
                )
            }

            totpSecretInformation?.let {
                requestTotpSecretUpdate(it)
            }
        }
    }

    private fun initWithTotpUri(totpUri: String) {
        getTotpSecret(totpUri).onFailure {
            Log.e(TAG, "Error parsing TOTP URI: $it")
            showTotpParseError()
        }.onSuccess { secret ->
            totpSecretInformation = secret
            viewModelScope.launch {
                val matchedItems = secret.issuer?.let {
                    registrableDomainResolver.resolve(it)
                }?.let {
                    passwordRepository.getVaultPasswordsByTLD(etld1 = it)
                }

                if (matchedItems.isNullOrEmpty()) {
                    updateUiWithTotpSecretInfo(secret)
                    return@launch
                }

                _base.update {
                    it.copy(
                        dialogState = DialogState.SelectItemForModification(
                            items = matchedItems
                        )
                    )
                }
            }
        }
    }

    fun onEvent(event: PasswordUiEvent) {
        when (event) {
            is PasswordUiEvent.OnSubmit -> {
                val ready = state.value as? PasswordUiState.Ready ?: return
                val base = ready.base
                viewModelScope.launch {
                    val upsert = itemId?.let { itemId ->
                        UpsertPassword.update(
                            itemId = itemId,
                            vaultId = ready.vaultsState.selectedVaultId,
                            name = fieldUpdate(base.nameTextFieldState.text.toString()),
                            username = fieldUpdate(base.usernameTextFieldState.text.toString()),
                            domains = set(base.domains),
                            password = fieldUpdate(base.passwordTextFieldState.text.toString()),
                            totpSecret = fieldUpdate(base.totpTextFieldState.text.toString()),
                            note = fieldUpdate(base.notesTextFieldState.text.toString())
                        )
                    } ?: UpsertPassword.create(
                        vaultId = ready.vaultsState.selectedVaultId,
                        name = base.nameTextFieldState.text.toString(),
                        username = base.usernameTextFieldState.text.toString(),
                        domains = base.domains,
                        password = base.passwordTextFieldState.text.toString(),
                        totpSecret = base.totpTextFieldState.text.toString(),
                        note = base.notesTextFieldState.text.toString()
                    )

                    createNewOrUpdatePassword(
                        upsert = upsert
                    ).onSuccess {
                        navigateUp(it)
                    }.onFailure { failure ->
                        _base.update {
                            it.copy(
                                nameError = if (failure.contains(PasswordError.BlankName)) InputFieldError.Empty else null,
                                passwordError = if (failure.contains(PasswordError.BlankPassword)) InputFieldError.Empty else null
                            )
                        }

                        if (failure.any { it is PasswordError.InvalidVaultId }) {
                            snackbarManager.sendMessage(
                                message = SnackbarMessage(
                                    message = ResourceString(R.string.invalid_vault_id)
                                )
                            )
                        }

                        if (failure.any { it is PasswordError.DatabaseError }) {
                            failure.filterIsInstance<PasswordError.DatabaseError>()
                                .first()
                                .let { dbError ->
                                    snackbarManager.sendMessage(
                                        message = SnackbarMessage(
                                            message = ResourceString(
                                                R.string.database_error,
                                                dbError.throwable.message ?: "no message"
                                            ),
                                        )
                                    )
                                }
                        }
                    }
                }
            }

            is PasswordUiEvent.OnGeneratePasswordClick -> {
                _base.update { it.copy(generatePasswordBottomSheetVisible = true) }
            }

            is PasswordUiEvent.OnBackClick -> {
                if (_base.value.scanning) {
                    _base.update { it.copy(scanning = false) }
                    return
                }

                navigateUp()
            }

            is PasswordUiEvent.OnCloseBottomSheet -> {
                _base.update { it.copy(generatePasswordBottomSheetVisible = false) }
            }

            is PasswordUiEvent.OnScanCodeRequest -> {
                _base.update { it.copy(scanning = true) }
            }

            is PasswordUiEvent.OnCodesScanned -> {
                event.codes.firstNotNullOfOrNull {
                    getTotpSecret(it).onFailure { failure ->
                        Log.e(TAG, "Error parsing TOTP URI: $failure")
                    }.getOrNull()
                }?.let {
                    _base.update { state ->
                        state.copy(scanning = false)
                    }

                    totpSecretInformation = it
                    requestTotpSecretUpdate(it)
                } ?: showTotpParseError()
            }

            is PasswordUiEvent.OnTotpModificationItemSelected -> {
                viewModelScope.launch { initWithId(event.itemId) }
            }

            is PasswordUiEvent.OnCreateNewItemForTotp -> {
                totpSecretInformation?.let {
                    updateUiWithTotpSecretInfo(it)
                }
            }

            is PasswordUiEvent.OnOverrideFieldClicked -> {
                _base.update {
                    it.copy(
                        dialogState = when (it.dialogState) {
                            is DialogState.OverrideTotp -> {
                                val fields = it.dialogState.fields.map { field ->
                                    if (field.fieldType == event.fieldType)
                                        field.copy(selected = !field.selected)
                                    else field
                                }.toSet()
                                it.dialogState.copy(fields = fields)
                            }

                            else -> it.dialogState
                        }
                    )
                }
            }

            is PasswordUiEvent.OnOverrideTotpFieldsConfirmed -> {
                val currentDialogState = _base.value.dialogState
                if (currentDialogState !is DialogState.OverrideTotp) return

                totpSecretInformation?.let {
                    val selectedFields =
                        currentDialogState.fields.filter { field -> field.selected }

                    selectedFields.applyToUi { after }
                }
            }

            is PasswordUiEvent.OnOverrideTotpFieldsKept -> {
                val currentDialogState = _base.value.dialogState
                if (currentDialogState !is DialogState.OverrideTotp) return

                totpSecretInformation?.let {
                    currentDialogState.fields.applyToUi { before }
                }
            }

            is PasswordUiEvent.OnTotpParseErrorDismiss -> {
                _base.update {
                    it.copy(
                        dialogState = DialogState.None,
                        scanning = false
                    )
                }
            }

            is PasswordUiEvent.OnAddDomains -> {
                itemId?.let { itemId ->
                    event.domains.forEach { domain ->
                        val registrableDomain = registrableDomainResolver.resolve(domain)
                        val info = DomainInfo(
                            loginId = itemId,
                            value = domain,
                            eTLD1 = registrableDomain
                        )
                        _base.update {
                            it.copy(domains = it.domains + info)
                        }
                    }
                }
            }

            is PasswordUiEvent.OnDeleteDomain -> {
                _base.update {
                    it.copy(
                        domains = it.domains.filterNot { info -> info.value == event.value }.toSet()
                    )
                }
            }

            is PasswordUiEvent.OnPasswordGenerated -> {
                passwordTextFieldState.setTextAndPlaceCursorAtEnd(event.password)
                _base.update {
                    it.copy(generatePasswordBottomSheetVisible = false)
                }
            }

            is PasswordUiEvent.OnVaultSelected -> {
                _selectedVaultId.value = event.vaultId
            }
        }
    }

    private fun Iterable<OverrideTotpField>.applyToUi(overrideWith: OverrideTotpField.() -> String) {
        val secretField = find { field -> field.fieldType == FieldType.Totp }
        val usernameField = find { field -> field.fieldType == FieldType.Username }
        val domainField = find { field -> field.fieldType == FieldType.Domain }


        updateUiWithSpecificTotpSecretInfo(
            secret = secretField?.overrideWith(),
            issuer = domainField?.overrideWith(),
            accountName = usernameField?.overrideWith()
        )
    }

    private fun requestTotpSecretUpdate(secretInformation: TotpSecretInformation) {
        val currentState = _base.value
        val currentTotpSecret = currentState.totpTextFieldState.text.toString()
        val currentIssuers = currentState.domains
        val currentAccountName = currentState.usernameTextFieldState.text.toString()

        val newTotpSecret = secretInformation.secret
        val newIssuer = secretInformation.issuer ?: ""
        val newAccountName = secretInformation.accountName

        val isCurrentSecretSet = currentTotpSecret.isNotBlank()
        val isCurrentAccountNameSet = currentAccountName.isNotBlank()

        val isCurrentTotpSecretSame = currentTotpSecret == newTotpSecret
        val isCurrentAccountNameSame = currentAccountName == newAccountName

        val overridingFields = mutableSetOf<OverrideTotpField>()

        val isOverridingTotpSecret = isCurrentSecretSet && !isCurrentTotpSecretSame
        val isAddingNewIssuer = currentIssuers
            .none { it.value.contains(newIssuer, ignoreCase = true) }
        val isOverridingAccountName = isCurrentAccountNameSet && !isCurrentAccountNameSame

        if (isOverridingTotpSecret) {
            overridingFields.add(
                OverrideTotpField(
                    fieldType = FieldType.Totp,
                    before = currentTotpSecret,
                    after = newTotpSecret
                )
            )
        }

        if (isOverridingAccountName) {
            overridingFields.add(
                OverrideTotpField(
                    fieldType = FieldType.Username,
                    before = currentAccountName,
                    after = newAccountName
                )
            )
        }

        updateUiWithSpecificTotpSecretInfo(
            secret = if (!isOverridingTotpSecret) newTotpSecret else null,
            issuer = if (isAddingNewIssuer) newIssuer else null,
            accountName = if (!isOverridingAccountName) newAccountName else null,
            closeDialog = false
        )

        if (overridingFields.isNotEmpty())
            _base.update {
                it.copy(dialogState = DialogState.OverrideTotp(fields = overridingFields))
            }
    }

    private fun updateUiWithTotpSecretInfo(secretInformation: TotpSecretInformation) =
        updateUiWithSpecificTotpSecretInfo(
            secret = secretInformation.secret,
            issuer = secretInformation.issuer,
            accountName = secretInformation.accountName
        )

    private fun updateUiWithSpecificTotpSecretInfo(
        secret: String? = null,
        issuer: String? = null,
        accountName: String? = null,
        closeDialog: Boolean = true
    ) {
        val currentState = _base.value
        secret?.let {
            currentState.totpTextFieldState.setTextAndPlaceCursorAtEnd(it)
        }

        issuer?.let {
            onEvent(PasswordUiEvent.OnAddDomains(setOf(it)))
        }

        accountName?.let {
            currentState.usernameTextFieldState.setTextAndPlaceCursorAtEnd(it)
        }

        if (closeDialog)
            _base.update {
                it.copy(
                    dialogState = DialogState.None,
                )
            }
    }

    private fun showTotpParseError() {
        _base.update {
            it.copy(
                dialogState = DialogState.TotpParseError,
                scanning = false
            )
        }
    }

    companion object {
        private const val TAG = "PasswordViewModel"
    }
}