package de.davis.keygo.item.create.presentation.password

import android.util.Log
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
import de.davis.keygo.R
import de.davis.keygo.core.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.domain.crypto.decryptSecretData
import de.davis.keygo.core.domain.estimator.PasswordStrengthEstimator
import de.davis.keygo.core.domain.model.snackbar.SnackbarMessage
import de.davis.keygo.core.domain.snackbar.SnackbarManager
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.ItemIdNone
import de.davis.keygo.core.item.domain.model.DomainInfo
import de.davis.keygo.core.item.domain.repository.PasswordRepository
import de.davis.keygo.core.item.domain.repository.VaultItemRepository
import de.davis.keygo.core.presentation.UIText
import de.davis.keygo.core.presentation.UIText.ResourceString
import de.davis.keygo.core.presentation.model.InputFieldError
import de.davis.keygo.core.presentation.model.NavigationEvent
import de.davis.keygo.core.util.domain.resolver.RegistrableDomainResolver
import de.davis.keygo.core.util.getOrNull
import de.davis.keygo.core.util.onFailure
import de.davis.keygo.core.util.onSuccess
import de.davis.keygo.item.core.domain.model.PasswordError
import de.davis.keygo.item.core.domain.model.UpsertPassword
import de.davis.keygo.item.core.domain.model.fieldUpdate
import de.davis.keygo.item.core.domain.model.set
import de.davis.keygo.item.core.domain.usecase.CreateNewOrUpdatePasswordUseCase
import de.davis.keygo.item.core.presentation.model.DetailPaneInformation
import de.davis.keygo.item.core.presentation.model.DetailType
import de.davis.keygo.item.core.presentation.password.model.FieldType
import de.davis.keygo.item.create.domain.PasswordGenerator
import de.davis.keygo.item.create.presentation.password.model.DialogState
import de.davis.keygo.item.create.presentation.password.model.GeneratePasswordUiEvent
import de.davis.keygo.item.create.presentation.password.model.OverrideTotpField
import de.davis.keygo.item.create.presentation.password.model.PasswordUiEvent
import de.davis.keygo.item.create.presentation.password.model.PasswordUiState
import de.davis.keygo.totp.domain.model.TotpSecretInformation
import de.davis.keygo.totp.domain.usecase.GetTotpSecretFromUrlUseCase
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel
import kotlin.time.Duration.Companion.milliseconds

@KoinViewModel
class PasswordViewModel(
    passwordGenerator: PasswordGenerator,
    private val vaultItemRepository: VaultItemRepository,
    private val passwordRepository: PasswordRepository,
    private val cryptographicScopeProvider: CryptographicScopeProvider,
    private val passwordStrengthEstimator: PasswordStrengthEstimator,
    private val createNewOrUpdatePassword: CreateNewOrUpdatePasswordUseCase,
    private val snackbarManager: SnackbarManager,
    private val getTotpSecret: GetTotpSecretFromUrlUseCase,
    private val registrableDomainResolver: RegistrableDomainResolver
) : GeneratePasswordViewModel(passwordGenerator, passwordStrengthEstimator) {

    private val nameTextFieldState = TextFieldState()
    private val passwordTextFieldState = TextFieldState()
    private val _uiState = MutableStateFlow(
        PasswordUiState(
            nameTextFieldState = nameTextFieldState,
            passwordTextFieldState = passwordTextFieldState
        )
    )

    val state = _uiState
        .onStart {
            observeNameTextField()
            observePasswordTextField()
            observeGenerator()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = _uiState.value
        )

    private val navigationEventChannel = Channel<NavigationEvent>()
    val navigationEvent = navigationEventChannel.receiveAsFlow()

    private var itemId = ItemIdNone
    private var totpSecretInformation: TotpSecretInformation? = null

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun observeNameTextField() {
        snapshotFlow { nameTextFieldState.text }
            .debounce(150.milliseconds)
            .mapLatest { input ->
                vaultItemRepository.doesNameExist(input.toString(), excludeId = itemId)
            }
            .distinctUntilChanged()
            .onEach { exists ->
                _uiState.update {
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
                _uiState.update {
                    it.copy(strengthScore = score)
                }
            }
            .flowOn(Dispatchers.Default)
            .launchIn(viewModelScope)
    }

    private fun observeGenerator() {
        generationState.onEach { state ->
            _uiState.update {
                it.copy(generatePasswordState = state)
            }
        }.launchIn(viewModelScope)

        finalPassword.onEach { password ->
            passwordTextFieldState.setTextAndPlaceCursorAtEnd(password)
            _uiState.update {
                it.copy(generatePasswordBottomSheetVisible = false)
            }
        }.launchIn(viewModelScope)
    }

    private fun navigateUp() {
        viewModelScope.launch {
            navigationEventChannel.send(NavigationEvent.NavigateBack)
        }
    }

    fun init(information: DetailPaneInformation) {
        when (information) {
            is DetailPaneInformation.InitByDetailType -> when (val type = information.detailType) {
                is DetailType.Modify.Edit -> initWithId(type.itemId)
                is DetailType.Modify.Totp -> initWithTotpUri(type.uri)
                is DetailType.Modify.CreateNew -> {} // Don't init anything
                is DetailType.View -> throw IllegalArgumentException("This VM cannot be used to view an item")
            }

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
                _uiState.update {
                    it.copy(
                        usernameTextFieldState = TextFieldState(createRaw.username),
                        domains = setOfNotNull(domainInfo).toImmutableSet(),
                        updating = false
                    )
                }
            }
        }
    }

    private fun initWithId(itemId: ItemId) {
        this.itemId = itemId
        if (itemId == ItemIdNone) return

        passwordRepository.observePasswordById(itemId)
            .onEach { password ->
                coroutineScope {
                    val pwdDeferred = async {
                        cryptographicScopeProvider.scope {
                            password.encryptedData.decryptSecretData()
                        }
                    }

                    val totpSecret = password.totpSecret?.let { totpSecret ->
                        async {
                            cryptographicScopeProvider.scope {
                                totpSecret.decryptSecretData()
                            }
                        }
                    }

                    nameTextFieldState.setTextAndPlaceCursorAtEnd(password.name)
                    passwordTextFieldState.setTextAndPlaceCursorAtEnd(pwdDeferred.await())

                    _uiState.update {
                        it.copy(
                            totpTextFieldState = TextFieldState(totpSecret?.await() ?: ""),
                            usernameTextFieldState = TextFieldState(password.username ?: ""),
                            domains = password.domainInfos.toImmutableSet(),
                            notesTextFieldState = TextFieldState(password.note ?: ""),
                            dialogState = DialogState.None,
                            updating = true
                        )
                    }

                    // Update the TOTP secret information if available
                    totpSecretInformation?.let {
                        requestTotpSecretUpdate(it)
                    }
                }
            }
            .flowOn(Dispatchers.Default)
            .launchIn(viewModelScope)
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

                _uiState.update {
                    it.copy(
                        dialogState = DialogState.SelectItemForModification(
                            items = matchedItems.toImmutableList()
                        )
                    )
                }
            }
        }
    }

    fun onEvent(event: PasswordUiEvent) {
        when (event) {
            is PasswordUiEvent.OnSubmit -> {
                viewModelScope.launch {
                    val state = _uiState.value
                    createNewOrUpdatePassword(
                        upsert = when (itemId == ItemIdNone) {
                            true -> UpsertPassword.create(
                                name = state.nameTextFieldState.text.toString(),
                                username = state.usernameTextFieldState.text.toString(),
                                domains = state.domains,
                                password = state.passwordTextFieldState.text.toString(),
                                totpSecret = state.totpTextFieldState.text.toString(),
                                note = state.notesTextFieldState.text.toString()
                            )

                            false -> UpsertPassword.update(
                                vaultId = itemId,
                                name = fieldUpdate(state.nameTextFieldState.text.toString()),
                                username = fieldUpdate(state.usernameTextFieldState.text.toString()),
                                domains = set(state.domains),
                                password = fieldUpdate(state.passwordTextFieldState.text.toString()),
                                totpSecret = fieldUpdate(state.totpTextFieldState.text.toString()),
                                note = fieldUpdate(state.notesTextFieldState.text.toString())
                            )
                        }
                    ).onSuccess {
                        navigateUp()
                    }.onFailure { failure ->
                        _uiState.update {
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
                                            message = UIText.ResourceString(
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
                _uiState.update { it.copy(generatePasswordBottomSheetVisible = true) }
            }

            is PasswordUiEvent.OnBackClick -> {
                if (_uiState.value.scanning) {
                    _uiState.update { it.copy(scanning = false) }
                    return
                }

                navigateUp()
            }

            is PasswordUiEvent.OnCloseBottomSheet -> {
                _uiState.update { it.copy(generatePasswordBottomSheetVisible = false) }
            }

            is GeneratePasswordUiEvent -> super.onEvent(event)

            is PasswordUiEvent.OnScanCodeRequest -> {
                _uiState.update { it.copy(scanning = true) }
            }

            is PasswordUiEvent.OnCodesScanned -> {
                event.codes.firstNotNullOfOrNull {
                    getTotpSecret(it).onFailure { failure ->
                        Log.e(TAG, "Error parsing TOTP URI: $failure")
                    }.getOrNull()
                }
                    ?.let {
                        _uiState.update { state ->
                            state.copy(scanning = false)
                        }

                        totpSecretInformation = it
                        requestTotpSecretUpdate(it)
                    } ?: showTotpParseError()
            }

            is PasswordUiEvent.OnTotpModificationItemSelected -> {
                initWithId(event.itemId)
            }

            is PasswordUiEvent.OnCreateNewItemForTotp -> {
                totpSecretInformation?.let {
                    updateUiWithTotpSecretInfo(it)
                }
            }

            is PasswordUiEvent.OnOverrideFieldClicked -> {
                _uiState.update {
                    it.copy(
                        dialogState = when (it.dialogState) {
                            is DialogState.OverrideTotp -> {
                                val fields = it.dialogState.fields.map { field ->
                                    if (field.fieldType == event.fieldType)
                                        field.copy(selected = !field.selected)
                                    else field
                                }.toImmutableList()
                                it.dialogState.copy(fields = fields)
                            }

                            else -> it.dialogState
                        }
                    )
                }
            }

            is PasswordUiEvent.OnOverrideTotpFieldsConfirmed -> {
                val currentDialogState = _uiState.value.dialogState
                if (currentDialogState !is DialogState.OverrideTotp) return

                totpSecretInformation?.let {
                    val selectedFields =
                        currentDialogState.fields.filter { field -> field.selected }

                    selectedFields.applyToUi { after }
                }
            }

            is PasswordUiEvent.OnOverrideTotpFieldsKept -> {
                val currentDialogState = _uiState.value.dialogState
                if (currentDialogState !is DialogState.OverrideTotp) return

                totpSecretInformation?.let {
                    currentDialogState.fields.applyToUi { before }
                }
            }

            is PasswordUiEvent.OnTotpParseErrorDismiss -> {
                _uiState.update {
                    it.copy(
                        dialogState = DialogState.None,
                        scanning = false
                    )
                }
            }

            is PasswordUiEvent.OnAddDomains -> {
                event.domains.forEach { domain ->
                    val registrableDomain = registrableDomainResolver.resolve(domain)
                    val info = DomainInfo(
                        passwordId = itemId,
                        value = domain,
                        eTLD1 = registrableDomain
                    )
                    _uiState.update {
                        val newList = it.domains + info
                        it.copy(domains = newList.toImmutableSet())
                    }
                }
            }

            is PasswordUiEvent.OnDeleteDomain -> {
                _uiState.update {
                    val newList = it.domains.filterNot { info -> info.value == event.value }
                    it.copy(domains = newList.toImmutableSet())
                }
            }
        }
    }

    private fun List<OverrideTotpField>.applyToUi(overrideWith: OverrideTotpField.() -> String) {
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
        val currentState = _uiState.value
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
            _uiState.update {
                it.copy(
                    dialogState = DialogState.OverrideTotp(
                        fields = overridingFields.toImmutableList()
                    )
                )
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
        val currentState = _uiState.value
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
            _uiState.update {
                it.copy(
                    dialogState = DialogState.None,
                )
            }
    }

    private fun showTotpParseError() {
        _uiState.update {
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