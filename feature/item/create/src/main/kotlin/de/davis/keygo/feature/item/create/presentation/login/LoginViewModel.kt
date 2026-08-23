package de.davis.keygo.feature.item.create.presentation.login

import android.util.Log
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.estimator.PasswordStrengthEstimator
import de.davis.keygo.core.item.domain.model.DomainInfo
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.item.domain.repository.ItemRepository
import de.davis.keygo.core.item.domain.repository.LoginRepository
import de.davis.keygo.core.item.domain.repository.VaultContextRepository
import de.davis.keygo.core.item.domain.repository.VaultRepository
import de.davis.keygo.core.item.domain.usecase.ObserveAllTagsSortedUseCase
import de.davis.keygo.core.security.domain.crypto.decrypt
import de.davis.keygo.core.security.domain.usecase.GetTdlMatchedLoginsUseCase
import de.davis.keygo.core.security.domain.usecase.ItemWithCryptoScopeUseCase
import de.davis.keygo.core.util.domain.model.snackbar.SnackbarMessage
import de.davis.keygo.core.util.domain.resolver.RegistrableDomainResolver
import de.davis.keygo.core.util.domain.snackbar.SnackbarManager
import de.davis.keygo.core.util.getOrNull
import de.davis.keygo.core.util.onFailure
import de.davis.keygo.core.util.onSuccess
import de.davis.keygo.core.util.presentation.UIText.Companion.ResourceString
import de.davis.keygo.feature.item.core.domain.model.ItemUpsertError
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
import de.davis.keygo.feature.item.create.presentation.login.model.LoginPasskeyInfo
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import kotlin.time.Duration.Companion.milliseconds

@KoinViewModel
internal class LoginViewModel(
    private val itemWithCryptoScope: ItemWithCryptoScopeUseCase,
    private val loginRepository: LoginRepository,
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

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val passwordScoreFlow = snapshotFlow { passwordTextFieldState.text }
        .debounce(150.milliseconds)
        .mapLatest { passwordStrengthEstimator(it.toString()) }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        // Scoring is dictionary-backed and debounced. Without a value up front, combine would keep
        // the whole form on ItemUiState.Loading until the first estimate lands.
        .onStart { emit(PasswordScore.None) }

    override val itemState: Flow<LoginBaseState> = combine(
        passwordScoreFlow,
        _base,
    ) { score, base ->
        base.copy(strengthScore = score)
    }

    private var totpSecretInformation: TotpInfo? = null
    private var totpOriginalUri: String? = null

    /**
     * Whether this screen was opened by a scanned code rather than by the user.
     *
     * Only a deep link owns the whole screen, so only it gets the picker and the back behaviour
     * that belongs to it. A code scanned from within the form arrives at an item the user already
     * chose and must keep leaving the screen as it always has.
     */
    private var totpItemPickerFlow = false

    /** Kept so returning to the picker shows the same suggestions without querying again. */
    private var totpSuggestedItemIds: Set<ItemId> = emptySet()

    /** The vault the picker was showing, restored when the form is abandoned back to it. */
    private var totpPickerVaultId: VaultId? = null

    /**
     * Shows a passkey for [rp] as pending until the item is saved.
     *
     * A blank id is dropped along with a null one. `rp.id` is optional per WebAuthn, so a request
     * that leaves it out reaches us as an empty string, which would otherwise show up as a blank
     * chip, make a name-only login look saveable, and leave the confirmation dialog asking about
     * nothing.
     */
    fun setPendingPasskeyCount(rp: String?) {
        if (rp.isNullOrBlank()) return
        _base.update {
            it.copy(passkeys = it.passkeys + LoginPasskeyInfo(rpId = rp, ref = null))
        }
    }

    fun init(information: DetailPaneInformation) {
        when (information) {
            is DetailPaneInformation.Init.Existing -> viewModelScope.launch {
                information.pendingTotpUri?.let { parsePendingTotp(it) }
                initWithId(information.id)
            }

            is DetailPaneInformation.Init.TOTP -> initWithTotpUri(information.uri)

            is DetailPaneInformation.Init.New -> information.pendingTotpUri?.let { uri ->
                parsePendingTotp(uri)?.let { updateUiWithTotpSecretInfo(it, uri) }
            }

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

        itemWithCryptoScope.oneShot(
            itemId = itemId,
            fetch = loginRepository::getLoginById,
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
                    passkeys = login.passkeys.mapTo(mutableSetOf()) { passkey ->
                        LoginPasskeyInfo(
                            rpId = passkey.rp,
                            ref = passkey,
                        )
                    },
                    deletedPasskeys = emptySet(),
                    dialogState = DialogState.None,
                    updating = true,
                )
            }

            totpSecretInformation?.let {
                requestTotpSecretUpdate(it, totpOriginalUri)
            }
        }
    }

    /**
     * Reads a code the picker handed over and remembers it, so [initWithId] can fold it into
     * whichever login was chosen. Returns null when the code cannot be read, having already put the
     * parse error on screen.
     */
    private fun parsePendingTotp(uri: String): TotpInfo? =
        totpService.getInfoFromUriWithResult(uri).onFailure { failure ->
            Log.e(TAG, "Error parsing TOTP URI: $failure")
            showTotpParseError()
        }.getOrNull()?.also {
            totpSecretInformation = it
            totpOriginalUri = uri
        }

    private fun initWithTotpUri(totpUri: String) {
        totpService.getInfoFromUriWithResult(totpUri).onFailure {
            Log.e(TAG, "Error parsing TOTP URI: $it")
            showTotpParseError()
        }.onSuccess { secret ->
            totpSecretInformation = secret
            totpOriginalUri = totpUri
            totpItemPickerFlow = true
            _base.update { it.copy(totpImportActive = true, selectingItemForTotp = true) }

            viewModelScope.launch {
                val suggestedIds = suggestedItemIdsFor(secret)
                totpSuggestedItemIds = suggestedIds
                // A choice made before the query returned leaves the picker; the late result has
                // nothing left to reorder until the user comes back to it.
                _base.update {
                    if (it.selectingItemForTotp) it.copy(totpSuggestedItemIds = suggestedIds)
                    else it
                }
            }
        }
    }

    /**
     * The logins whose registrable domain matches the code's own.
     *
     * [resolveTotpDomain] is what fills the domain field for a new item, so the suggestions agree
     * with it: a code that carries no issuer still matches on the domain in `user@example.com`.
     */
    private suspend fun suggestedItemIdsFor(secretInformation: TotpInfo): Set<ItemId> {
        val domain = resolveTotpDomain(
            issuer = secretInformation.issuer,
            accountName = secretInformation.accountName,
        ) ?: return emptySet()

        return getTdlMatchedLogins(domain).mapTo(mutableSetOf()) { it.id }
    }

    override fun onSubmit() {
        val ready = state.value as? ItemUiState.Ready ?: return
        val base = ready.base
        val assignedTags = ready.shared.itemAssignedTags
        val selectedVaultId = ready.shared.vaultsState.selectedVaultId
        // Independent: a save can both register a passkey and drop another one.
        val pendingPasskey = base.passkeys.any { it.pending }
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
                    removedPasskeys = base.deletedPasskeys,
                    pendingPasskey = pendingPasskey,
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
                pendingPasskey = pendingPasskey,
            )

            createNewOrUpdateLogin(
                upsert = upsert,
            ).onSuccess {
                navigateUp(it)
            }.onFailure { failure ->
                _base.update {
                    it.copy(
                        nameError = if (failure.contains(ItemUpsertError.BlankName)) InputFieldError.Empty else null,
                    )
                }

                if (failure.any { it is ItemUpsertError.InvalidVaultId }) {
                    snackbarManager.sendMessage(
                        message = SnackbarMessage(
                            message = ResourceString(R.string.invalid_vault_id),
                        ),
                    )
                }

                failure.filterIsInstance<ItemUpsertError.DatabaseError>()
                    .firstOrNull()
                    ?.let { dbError ->
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

    override fun onBackClick() {
        if (_base.value.scanning) {
            _base.update { it.copy(scanning = false) }
            return
        }

        // Back belongs to the import while one is running: from the form it returns to the picker
        // the form was opened from. The picker itself is the flow's first step and has nothing
        // behind it, so leaving it is the screen's own business (it closes the app) and never a
        // navigation back to a dashboard the user did not open.
        if (totpItemPickerFlow) {
            if (!_base.value.selectingItemForTotp) reopenTotpItemPicker()
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
                closeTotpItemPicker()
                viewModelScope.launch { initWithId(event.itemId) }
            }

            is LoginUiEvent.OnCreateNewItemForTotp -> {
                closeTotpItemPicker()
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

            is LoginUiEvent.OnDeletePasskeyRequest -> {
                _base.update {
                    it.copy(dialogState = DialogState.DeletePasskey(passkey = event.passkey))
                }
            }

            is LoginUiEvent.OnPasskeyDeletionDismiss -> {
                _base.update { it.copy(dialogState = DialogState.None) }
            }

            is LoginUiEvent.OnConfirmPasskeyDeletion -> {
                val dialogState = _base.value.dialogState as? DialogState.DeletePasskey ?: return

                _base.update {
                    it.copy(
                        passkeys = it.passkeys
                            .filterNot { passkey -> passkey.ref == dialogState.passkey }
                            .toSet(),
                        deletedPasskeys = it.deletedPasskeys + dialogState.passkey,
                        dialogState = DialogState.None,
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

    private fun closeTotpItemPicker() {
        totpPickerVaultId = selectedVaultId.value
        _base.update {
            it.copy(selectingItemForTotp = false, totpSuggestedItemIds = emptySet())
        }
    }

    /**
     * Returns to the picker, throwing away whatever the choice made of the form.
     *
     * The form is rebuilt from scratch rather than hidden: the choice may have loaded an existing
     * item into it, and carrying that item's name, password or vault into the next choice would
     * write it onto the wrong login. Only the scanned code and its suggestions survive, because
     * those belong to the import rather than to the item.
     */
    private fun reopenTotpItemPicker() {
        itemId = null
        nameTextFieldState.setTextAndPlaceCursorAtEnd("")
        notesTextFieldState.setTextAndPlaceCursorAtEnd("")
        passwordTextFieldState.setTextAndPlaceCursorAtEnd("")
        setAssignedTags(emptySet())
        totpPickerVaultId?.let { setSelectedVaultId(it) }

        _base.value = LoginBaseState(
            passwordTextFieldState = passwordTextFieldState,
            totpImportActive = true,
            selectingItemForTotp = true,
            totpSuggestedItemIds = totpSuggestedItemIds,
        )
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
