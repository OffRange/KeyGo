package de.davis.keygo.feature.item.create.presentation.login

import android.util.Log
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.estimator.PasswordStrengthEstimator
import de.davis.keygo.core.item.domain.model.DomainInfo
import de.davis.keygo.core.item.domain.repository.ItemRepository
import de.davis.keygo.core.item.domain.repository.VaultContextRepository
import de.davis.keygo.core.item.domain.repository.VaultRepository
import de.davis.keygo.core.item.domain.usecase.ObserveAllTagsSortedUseCase
import de.davis.keygo.core.security.domain.crypto.decrypt
import de.davis.keygo.core.security.domain.usecase.GetTdlMatchedLoginsUseCase
import de.davis.keygo.core.security.domain.usecase.LoginWithCryptoScopeUseCase
import de.davis.keygo.core.util.domain.model.snackbar.SnackbarMessage
import de.davis.keygo.core.util.domain.resolver.RegistrableDomainResolver
import de.davis.keygo.core.util.domain.snackbar.SnackbarManager
import de.davis.keygo.core.util.getOrNull
import de.davis.keygo.core.util.onFailure
import de.davis.keygo.core.util.onSuccess
import de.davis.keygo.core.util.presentation.UIText.Companion.ResourceString
import de.davis.keygo.feature.item.core.domain.model.LoginError
import de.davis.keygo.feature.item.core.domain.model.UpsertLogin
import de.davis.keygo.feature.item.core.domain.model.fieldUpdate
import de.davis.keygo.feature.item.core.domain.model.resolveTotpDomain
import de.davis.keygo.feature.item.core.domain.model.set
import de.davis.keygo.feature.item.core.domain.usecase.CreateNewOrUpdateLoginUseCase
import de.davis.keygo.feature.item.core.presentation.login.model.FieldType
import de.davis.keygo.feature.item.core.presentation.model.DetailPaneInformation
import de.davis.keygo.feature.item.core.presentation.model.InputFieldError
import de.davis.keygo.feature.item.create.R
import de.davis.keygo.feature.item.create.presentation.ItemViewModel
import de.davis.keygo.feature.item.create.presentation.login.model.DialogState
import de.davis.keygo.feature.item.create.presentation.login.model.LoginBaseState
import de.davis.keygo.feature.item.create.presentation.login.model.LoginUiEvent
import de.davis.keygo.feature.item.create.presentation.login.model.OverrideTotpField
import de.davis.keygo.feature.item.create.presentation.model.ItemUiState
import de.davis.keygo.rust.totp.TotpService
import de.davis.keygo.rust.totp.getInfoFromUriWithResult
import de.davis.keygo.rust.totp.getUrlWithResult
import de.davisalessandro.keygo.rust.TotpInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import kotlin.time.Duration.Companion.milliseconds

@KoinViewModel
internal class LoginViewModel(
    private val loginWithCryptoScope: LoginWithCryptoScopeUseCase,
    private val passwordStrengthEstimator: PasswordStrengthEstimator,
    private val createNewOrUpdateLogin: CreateNewOrUpdateLoginUseCase,
    private val getTdlMatchedLogins: GetTdlMatchedLoginsUseCase,
    private val snackbarManager: SnackbarManager,
    private val totpService: TotpService,
    private val registrableDomainResolver: RegistrableDomainResolver,
    vaultContextRepository: VaultContextRepository,
    itemRepository: ItemRepository,
    observeAllTags: ObserveAllTagsSortedUseCase,
    vaultRepository: VaultRepository,
) : ItemViewModel<LoginBaseState>(
    vaultContextRepository = vaultContextRepository,
    itemRepository = itemRepository,
    observeAllTags = observeAllTags,
    vaultRepository = vaultRepository,
) {

    private val passwordTextFieldState = TextFieldState()
    private val _base = MutableStateFlow(
        LoginBaseState(
            passwordTextFieldState = passwordTextFieldState,
        )
    )

    override val itemState: Flow<LoginBaseState> = _base

    override suspend fun onSubscribed() {
        observePasswordTextField()
    }

    private var totpSecretInformation: TotpInfo? = null
    private var totpOriginalUri: String? = null

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

    fun setPendingPasskeyCount(count: Int) {
        _base.update { it.copy(pendingPasskeyCount = count) }
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
            is DetailPaneInformation.CreateRaw.Login -> {
                val domainInfo = createRaw.url?.let {
                    val eTLD1 = registrableDomainResolver.resolve(it)
                    DomainInfo(
                        value = it,
                        eTLD1 = eTLD1,
                    )
                }

                passwordTextFieldState.setTextAndPlaceCursorAtEnd(createRaw.password)
                _base.update {
                    it.copy(
                        usernameTextFieldState = TextFieldState(createRaw.username),
                        domains = setOfNotNull(domainInfo),
                        updating = false,
                    )
                }
            }
        }
    }

    private suspend fun initWithId(itemId: ItemId) {
        this.itemId = itemId

        loginWithCryptoScope.oneShot(
            itemId = itemId,
        ) { login ->
            val decrypted = coroutineScope {
                val pwdDeferred = login.passwordCredential?.let { pwd ->
                    async { pwd.secret.decrypt() }
                }
                val totpDeferred = login.totp?.let { totp ->
                    async {
                        val secret = totp.secret.decrypt()
                        totp.accountName?.let { accountName ->
                            totpService.getUrlWithResult(
                                totp.algorithm,
                                totp.digits,
                                totp.period,
                                secret,
                                totp.issuer,
                                accountName
                            ).getOrNull()
                        } ?: secret
                    }
                }
                pwdDeferred?.await() to totpDeferred?.await()
            }

            nameTextFieldState.setTextAndPlaceCursorAtEnd(login.name)
            notesTextFieldState.setTextAndPlaceCursorAtEnd(login.note ?: "")
            passwordTextFieldState.setTextAndPlaceCursorAtEnd(decrypted.first ?: "")

            setSelectedVaultId(login.vaultId)
            setAssignedTags(login.tags)
            _base.update {
                it.copy(
                    totpTextFieldState = TextFieldState(decrypted.second ?: ""),
                    usernameTextFieldState = TextFieldState(login.username ?: ""),
                    domains = login.domainInfos,
                    existingPasskeyCount = login.passkeyRPs.size,
                    dialogState = DialogState.None,
                    updating = true,
                )
            }

            totpSecretInformation?.let {
                requestTotpSecretUpdate(it, totpOriginalUri)
            }
        }
    }

    private fun initWithTotpUri(totpUri: String) {
        totpService.getInfoFromUriWithResult(totpUri).onFailure {
            Log.e(TAG, "Error parsing TOTP URI: $it")
            showTotpParseError()
        }.onSuccess { secret ->
            totpSecretInformation = secret
            totpOriginalUri = totpUri
            viewModelScope.launch {
                val matchedItems = secret.issuer?.let {
                    getTdlMatchedLogins(it)
                }

                if (matchedItems.isNullOrEmpty()) {
                    updateUiWithTotpSecretInfo(secret, totpUri)
                    return@launch
                }

                _base.update {
                    it.copy(
                        dialogState = DialogState.SelectItemForModification(
                            items = matchedItems,
                        )
                    )
                }
            }
        }
    }

    override fun onSubmit() {
        val ready = state.value as? ItemUiState.Ready ?: return
        val base = ready.base
        val assignedTags = ready.shared.itemAssignedTags
        val selectedVaultId = ready.shared.vaultsState.selectedVaultId
        viewModelScope.launch {
            val upsert = itemId?.let { itemId ->
                UpsertLogin.update(
                    itemId = itemId,
                    vaultId = selectedVaultId,
                    name = fieldUpdate(nameTextFieldState.text.toString()),
                    username = fieldUpdate(base.usernameTextFieldState.text.toString()),
                    domains = set(base.domains),
                    tags = set(assignedTags),
                    password = fieldUpdate(base.passwordTextFieldState.text.toString()),
                    totpUriOrSecret = fieldUpdate(base.totpTextFieldState.text.toString()),
                    note = fieldUpdate(notesTextFieldState.text.toString()),
                )
            } ?: UpsertLogin.create(
                vaultId = selectedVaultId,
                name = nameTextFieldState.text.toString(),
                username = base.usernameTextFieldState.text.toString(),
                domains = base.domains,
                tags = assignedTags,
                password = base.passwordTextFieldState.text.toString(),
                totpUriOrSecret = base.totpTextFieldState.text.toString(),
                note = notesTextFieldState.text.toString(),
                hasPendingPasskey = base.pendingPasskeyCount > 0,
            )

            createNewOrUpdateLogin(
                upsert = upsert,
            ).onSuccess {
                navigateUp(it)
            }.onFailure { failure ->
                _base.update {
                    it.copy(
                        nameError = if (failure.contains(LoginError.BlankName)) InputFieldError.Empty else null,
                    )
                }

                if (failure.any { it is LoginError.InvalidVaultId }) {
                    snackbarManager.sendMessage(
                        message = SnackbarMessage(
                            message = ResourceString(R.string.invalid_vault_id),
                        ),
                    )
                }

                if (failure.any { it is LoginError.DatabaseError }) {
                    failure.filterIsInstance<LoginError.DatabaseError>()
                        .first()
                        .let { dbError ->
                            snackbarManager.sendMessage(
                                message = SnackbarMessage(
                                    message = ResourceString(
                                        R.string.database_error,
                                        dbError.throwable.message ?: "no message",
                                    ),
                                ),
                            )
                        }
                }
            }
        }
    }

    override fun onBackClick() {
        if (_base.value.scanning) {
            _base.update { it.copy(scanning = false) }
            return
        }

        navigateUp()
    }

    fun onEvent(event: LoginUiEvent) {
        when (event) {
            is LoginUiEvent.ItemUi -> onItemUiEvent(event.event)

            is LoginUiEvent.OnGeneratePasswordClick -> {
                _base.update { it.copy(generatePasswordBottomSheetVisible = true) }
            }

            is LoginUiEvent.OnCloseBottomSheet -> {
                _base.update { it.copy(generatePasswordBottomSheetVisible = false) }
            }

            is LoginUiEvent.OnScanCodeRequest -> {
                _base.update { it.copy(scanning = true) }
            }

            is LoginUiEvent.OnCodesScanned -> {
                event.codes.firstNotNullOfOrNull { code ->
                    totpService.getInfoFromUriWithResult(code).onFailure { failure ->
                        Log.e(TAG, "Error parsing TOTP URI: $failure")
                    }.getOrNull()?.let { code to it }
                }?.let { (scannedUri, secretInfo) ->
                    _base.update { state ->
                        state.copy(scanning = false)
                    }

                    totpOriginalUri = scannedUri
                    totpSecretInformation = secretInfo
                    requestTotpSecretUpdate(secretInfo, scannedUri)
                } ?: showTotpParseError()
            }

            is LoginUiEvent.OnTotpModificationItemSelected -> {
                viewModelScope.launch { initWithId(event.itemId) }
            }

            is LoginUiEvent.OnCreateNewItemForTotp -> {
                totpSecretInformation?.let {
                    updateUiWithTotpSecretInfo(it, totpOriginalUri)
                }
            }

            is LoginUiEvent.OnOverrideFieldClicked -> {
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

            is LoginUiEvent.OnOverrideTotpFieldsConfirmed -> {
                val currentDialogState = _base.value.dialogState
                if (currentDialogState !is DialogState.OverrideTotp) return

                totpSecretInformation?.let {
                    val selectedFields =
                        currentDialogState.fields.filter { field -> field.selected }

                    selectedFields.applyToUi { after }
                }
            }

            is LoginUiEvent.OnOverrideTotpFieldsKept -> {
                val currentDialogState = _base.value.dialogState
                if (currentDialogState !is DialogState.OverrideTotp) return

                totpSecretInformation?.let {
                    currentDialogState.fields.applyToUi { before }
                }
            }

            is LoginUiEvent.OnTotpParseErrorDismiss -> {
                _base.update {
                    it.copy(
                        dialogState = DialogState.None,
                        scanning = false,
                    )
                }
            }

            is LoginUiEvent.OnAddDomains -> {
                event.domains.forEach { domain ->
                    val registrableDomain = registrableDomainResolver.resolve(domain)
                    val info = DomainInfo(
                        loginId = itemId,
                        value = domain,
                        eTLD1 = registrableDomain,
                    )
                    _base.update {
                        it.copy(domains = it.domains + info)
                    }
                }
            }

            is LoginUiEvent.OnDeleteDomain -> {
                _base.update {
                    it.copy(
                        domains = it.domains.filterNot { info -> info.value == event.value }
                            .toSet(),
                    )
                }
            }

            is LoginUiEvent.OnPasswordGenerated -> {
                passwordTextFieldState.setTextAndPlaceCursorAtEnd(event.password)
                _base.update {
                    it.copy(generatePasswordBottomSheetVisible = false)
                }
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
            accountName = usernameField?.overrideWith(),
        )
    }

    private fun requestTotpSecretUpdate(
        secretInformation: TotpInfo,
        originalUri: String? = null,
    ) {
        val currentState = _base.value
        val currentTotpSecret = currentState.totpTextFieldState.text.toString()
        val currentIssuers = currentState.domains
        val currentAccountName = currentState.usernameTextFieldState.text.toString()

        val newTotpSecret = originalUri ?: secretInformation.secret
        val newDomain = resolveTotpDomain(secretInformation.issuer, secretInformation.accountName)
        val newAccountName = secretInformation.accountName

        val isCurrentSecretSet = currentTotpSecret.isNotBlank()
        val isCurrentAccountNameSet = currentAccountName.isNotBlank()

        val isCurrentTotpSecretSame = currentTotpSecret == newTotpSecret
        val isCurrentAccountNameSame = currentAccountName == newAccountName

        val overridingFields = mutableSetOf<OverrideTotpField>()

        val isOverridingTotpSecret = isCurrentSecretSet && !isCurrentTotpSecretSame
        val isAddingNewIssuer = newDomain != null && currentIssuers
            .none { it.value.contains(newDomain, ignoreCase = true) }
        val isOverridingAccountName = isCurrentAccountNameSet && !isCurrentAccountNameSame

        if (isOverridingTotpSecret) {
            overridingFields.add(
                OverrideTotpField(
                    fieldType = FieldType.Totp,
                    before = currentTotpSecret,
                    after = newTotpSecret,
                )
            )
        }

        if (isOverridingAccountName) {
            overridingFields.add(
                OverrideTotpField(
                    fieldType = FieldType.Username,
                    before = currentAccountName,
                    after = newAccountName,
                )
            )
        }

        updateUiWithSpecificTotpSecretInfo(
            secret = if (!isOverridingTotpSecret) newTotpSecret else null,
            issuer = if (isAddingNewIssuer) newDomain else null,
            accountName = if (!isOverridingAccountName) newAccountName else null,
            closeDialog = false,
        )

        if (overridingFields.isNotEmpty())
            _base.update {
                it.copy(dialogState = DialogState.OverrideTotp(fields = overridingFields))
            }
    }

    private fun updateUiWithTotpSecretInfo(
        secretInformation: TotpInfo,
        originalUri: String? = null,
    ) = updateUiWithSpecificTotpSecretInfo(
        secret = originalUri ?: secretInformation.secret,
        issuer = resolveTotpDomain(secretInformation.issuer, secretInformation.accountName),
        accountName = secretInformation.accountName,
    )

    private fun updateUiWithSpecificTotpSecretInfo(
        secret: String? = null,
        issuer: String? = null,
        accountName: String? = null,
        closeDialog: Boolean = true,
    ) {
        val currentState = _base.value
        secret?.let {
            currentState.totpTextFieldState.setTextAndPlaceCursorAtEnd(it)
        }

        issuer?.let {
            onEvent(LoginUiEvent.OnAddDomains(setOf(it)))
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
                scanning = false,
            )
        }
    }

    companion object {
        private const val TAG = "LoginViewModel"
    }
}
