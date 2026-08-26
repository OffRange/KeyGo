package de.davis.keygo.feature.autofill.presentation.activity

import android.util.Patterns
import androidx.core.util.PatternsCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.identity.domain.model.UnlockError
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.Login
import de.davis.keygo.core.item.domain.repository.ItemRepository
import de.davis.keygo.core.item.domain.repository.LoginRepository
import de.davis.keygo.core.item.domain.repository.TotpRepository
import de.davis.keygo.core.item.domain.repository.VaultRepository
import de.davis.keygo.core.security.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.security.domain.crypto.decrypt
import de.davis.keygo.core.security.domain.crypto.model.WrappedVaultKeyInformation
import de.davis.keygo.core.security.domain.crypto.wrappedItemKeyInformation
import de.davis.keygo.core.security.domain.model.BiometricAuthError
import de.davis.keygo.core.util.getOrNull
import de.davis.keygo.core.util.onFailure
import de.davis.keygo.core.util.onSuccess
import de.davis.keygo.feature.autofill.domain.usecase.AddRegistrableDomainsToLoginUseCase
import de.davis.keygo.feature.autofill.domain.usecase.DoesItemHaveDomainReferencesUseCase
import de.davis.keygo.feature.autofill.domain.usecase.IsAppLinkedToWebsiteUseCase
import de.davis.keygo.feature.autofill.presentation.AutofillDatasetProvider
import de.davis.keygo.feature.autofill.presentation.activity.model.AssociationDialogVisibility
import de.davis.keygo.feature.autofill.presentation.activity.model.AutofillBiometricRequest
import de.davis.keygo.feature.autofill.presentation.activity.model.AutofillEvent
import de.davis.keygo.feature.autofill.presentation.activity.model.AutofillUiEvent
import de.davis.keygo.feature.autofill.presentation.activity.model.SuspicionDialogVisibility
import de.davis.keygo.feature.autofill.presentation.model.AutofillUiState
import de.davis.keygo.feature.autofill.presentation.model.AutofillValue
import de.davis.keygo.feature.autofill.presentation.model.FieldType
import de.davis.keygo.feature.autofill.presentation.model.FillRequestData
import de.davis.keygo.feature.autofill.presentation.model.Form
import de.davis.keygo.feature.autofill.presentation.model.FormType
import de.davis.keygo.feature.autofill.presentation.model.Request
import de.davis.keygo.feature.autofill.presentation.model.RequestData
import de.davis.keygo.feature.autofill.presentation.model.SaveRequestData
import de.davis.keygo.feature.autofill.presentation.sms.SmsCodeFailure
import de.davis.keygo.feature.autofill.presentation.sms.SmsCodeRepository
import de.davis.keygo.feature.item.core.presentation.model.DetailPaneInformation
import de.davis.keygo.feature.totp.domain.repository.TotpGenerator
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class AutofillViewModel(
    savedStateHandle: SavedStateHandle,
    private val vaultRepository: VaultRepository,
    private val loginRepository: LoginRepository,
    private val totpRepository: TotpRepository,
    private val itemRepository: ItemRepository,
    private val smsCodeRepository: SmsCodeRepository,
    private val cryptographicScopeProvider: CryptographicScopeProvider,
    private val autofillDatasetProvider: AutofillDatasetProvider,
    private val doesItemHaveDomainReferences: DoesItemHaveDomainReferencesUseCase,
    private val addRegistrableDomainToLogin: AddRegistrableDomainsToLoginUseCase,
    private val isAppLinkedToWebsite: IsAppLinkedToWebsiteUseCase,
    private val totpGenerator: TotpGenerator,
) : ViewModel() {

    private val requestData = savedStateHandle.get<RequestData>(KEY_AUTOFILL_INFORMATION)
        ?: throw IllegalArgumentException("Extraction must not be null")

    private val biometricChannel = Channel<AutofillBiometricRequest>()
    val biometricFlow = biometricChannel.receiveAsFlow()

    private val eventChannel = Channel<AutofillEvent>()
    val events = eventChannel.receiveAsFlow()

    private val _uiState = MutableStateFlow(AutofillUiState())
    val uiState = _uiState.asStateFlow()

    private var smsOtpJob: Job? = null

    fun start() {
        handleRequestData()
    }

    private fun Form.toRawItem() = when (type) {
        // TODO: maybe find suitable names
        is FormType.Credentials -> DetailPaneInformation.CreateRaw.Login(
            name = "",
            password = fields.find { it.type == FieldType.Credentials.Password }?.autofillValue
                ?: "",
            username = fields.find { it.type == FieldType.Credentials.Username }?.autofillValue
                ?: fields.find { it.type == FieldType.Credentials.EMail }?.autofillValue
                ?: fields.find { it.type == FieldType.Credentials.Phone }?.autofillValue
                ?: "",
            url = url,
        )

        is FormType.TOTP -> throw IllegalArgumentException("TOTP not supported for saving")
    }

    private fun handleSaveRequest(requestData: SaveRequestData) {
        _uiState.update { it.copy(request = Request.SaveItem(requestData.form.toRawItem())) }
    }

    private fun handleRequestData(ignoreSuspicion: Boolean = false) =
        viewModelScope.launch {
            val handleSuspicion = !ignoreSuspicion && requestData.form.isSuspicious
            val linked = when {
                handleSuspicion -> requestData.form.url?.let {
                    isAppLinkedToWebsite(
                        packageName = requestData.form.appPackageName,
                        domain = it
                    )
                } == true

                else -> false
            }

            val showSuspicionDialog = !linked && handleSuspicion

            _uiState.update {
                it.copy(
                    suspicionDialogVisibility = if (showSuspicionDialog)
                        SuspicionDialogVisibility.Visible(
                            appPackageName = requestData.form.appPackageName,
                            website = requestData.form.url.orEmpty()
                        )
                    else SuspicionDialogVisibility.Hidden
                )
            }

            if (showSuspicionDialog) return@launch

            when (requestData) {
                is SaveRequestData -> handleSaveRequest(requestData)

                is FillRequestData.Pinned,
                is FillRequestData.App -> _uiState.update { it.copy(request = Request.SelectItem) }

                is FillRequestData.GeneratePassword -> _uiState.update {
                    it.copy(showGeneratePassword = true)
                }

                is FillRequestData.SmsOtp -> handleSmsOtpRequest(requestData)

                is FillRequestData.Suggestion -> handleSuggestionRequest(requestData)
            }
        }

    private suspend fun handleSuggestionRequest(suggestionInfo: FillRequestData.Suggestion) {
        _uiState.update { it.copy(itemId = suggestionInfo.vaultId) }

        val itemName = itemRepository.getItemName(suggestionInfo.vaultId)
            ?: throw IllegalArgumentException("Name for vaultId=${suggestionInfo.vaultId} not found")

        biometricChannel.send(AutofillBiometricRequest.UnlockItem(itemName))
    }

    private suspend fun handleSmsOtpRequest(smsOtpInfo: FillRequestData.SmsOtp) {
        // The extractor already narrowed the form to the focused field's group, so a TOTP form holds
        // TOTP fields and nothing else. An empty one means there is nothing to fill.
        if (smsOtpInfo.form.fields.isEmpty()) {
            eventChannel.send(AutofillEvent.Abort)
            return
        }

        _uiState.update { it.copy(showSmsPending = true) }
        startSmsRetrieval()
    }

    private fun startSmsRetrieval(consentAlreadyRequested: Boolean = false) {
        smsOtpJob?.cancel()
        smsOtpJob = viewModelScope.launch {
            smsCodeRepository.retrieveSmsCode()
                .onSuccess { code -> sendSmsFillEvent(code) }
                .onFailure { failure ->
                    when (failure) {
                        // Asking a second time would mean the consent screen came back OK without
                        // actually granting anything, so stop rather than spin.
                        is SmsCodeFailure.ConsentRequired ->
                            if (consentAlreadyRequested) eventChannel.send(AutofillEvent.Abort)
                            else eventChannel.send(
                                AutofillEvent.RequestSmsConsent(failure.intentSender),
                            )

                        else -> eventChannel.send(AutofillEvent.Abort)
                    }
                }
        }
    }

    private fun onSmsConsentResult(granted: Boolean) {
        if (granted) startSmsRetrieval(consentAlreadyRequested = true)
        else viewModelScope.launch { eventChannel.send(AutofillEvent.Abort) }
    }

    private fun cancelSmsRetrieval() {
        smsOtpJob?.cancel()
        smsOtpJob = null
        _uiState.update { it.copy(showSmsPending = false) }
        viewModelScope.launch { eventChannel.send(AutofillEvent.Abort) }
    }

    private suspend fun sendSmsFillEvent(code: String) {
        val targetField = (requestData as? FillRequestData.SmsOtp)?.form?.fields?.firstOrNull() ?: run {
            eventChannel.send(AutofillEvent.Abort)
            return
        }

        _uiState.update { it.copy(showSmsPending = false) }
        eventChannel.send(
            AutofillEvent.Fill(
                autofillDatasetProvider.getFillingDataset(
                    listOf(
                        AutofillValue(
                            autofillId = targetField.autofillId,
                            value = code,
                        ),
                    ),
                ),
            ),
        )
    }

    fun onBiometricLoginFailed(error: UnlockError) {
        viewModelScope.launch {
            when(error) {
                is UnlockError.BiometricFailed -> {
                    when(error.error) {
                        BiometricAuthError.Canceled -> eventChannel.send(AutofillEvent.Abort)
                        else -> _uiState.update { it.copy(request = Request.JustAuthenticateWithPwd) }
                    }
                }

                else -> _uiState.update { it.copy(request = Request.JustAuthenticateWithPwd) }
            }
        }
    }

    fun onBiometricLoginSucceeded() {
        viewModelScope.launch {
            if (requestData is FillRequestData.Suggestion) {
                sendFillEvent(requestData.vaultId)
                return@launch
            }
        }
    }

    private fun onItemSelected(vaultId: ItemId) {
        viewModelScope.launch {
            val fillRightAway = doesItemHaveDomainReferences(
                itemVaultId = vaultId,
                domain = requestData.form.url.orEmpty()
            )

            if (fillRightAway) {
                sendFillEvent(vaultId)
                return@launch
            }

            val itemName = itemRepository.getItemName(vaultId)
                ?: throw IllegalArgumentException("Name for vaultId=$vaultId not found")

            _uiState.update {
                it.copy(
                    associationDialogVisibility = AssociationDialogVisibility.Visible(
                        itemName = itemName,
                        domain = requestData.form.url
                    ),
                    itemId = vaultId
                )
            }
        }
    }

    private fun onAuthenticated() {
        viewModelScope.launch {
            if (requestData is FillRequestData.Suggestion) {
                sendFillEvent(requestData.vaultId)
                return@launch
            }

            eventChannel.send(AutofillEvent.Abort)
        }
    }

    fun onEvent(event: AutofillUiEvent) {
        when (event) {
            AutofillUiEvent.OnAssociate -> associateItem()
            AutofillUiEvent.OnAuthenticated -> onAuthenticated()
            AutofillUiEvent.OnCancelAssociation -> hideAssociationDialog()
            is AutofillUiEvent.OnItemSelected -> onItemSelected(event.itemId)

            AutofillUiEvent.OnContinueInSuspicion -> {
                _uiState.update { it.copy(suspicionDialogVisibility = SuspicionDialogVisibility.Hidden) }
                handleRequestData(ignoreSuspicion = true)
            }

            AutofillUiEvent.OnAbortInSuspicion -> viewModelScope.launch {
                eventChannel.send(AutofillEvent.Abort)
            }

            AutofillUiEvent.OnDismissGeneratePassword -> viewModelScope.launch {
                eventChannel.send(AutofillEvent.Abort)
            }

            is AutofillUiEvent.OnGeneratedPassword -> viewModelScope.launch {
                sendGeneratedPasswordFillEvent(event.password)
            }

            is AutofillUiEvent.OnSmsConsentResult -> onSmsConsentResult(event.granted)
            AutofillUiEvent.OnCancelSmsCode -> cancelSmsRetrieval()
        }
    }

    private fun associateItem() {
        uiState.value.itemId?.let { itemId ->
            requestData.form.url?.let {
                viewModelScope.launch {
                    addRegistrableDomainToLogin(
                        loginId = itemId,
                        domain = it,
                    )
                }
            }
        }
        hideAssociationDialog()
    }

    private fun hideAssociationDialog() {
        _uiState.update { it.copy(associationDialogVisibility = AssociationDialogVisibility.Hidden) }
        _uiState.value.itemId?.let { itemId ->
            viewModelScope.launch {
                sendFillEvent(itemId)
            }
        }
    }

    private suspend fun sendFillEvent(itemId: ItemId) {
        if (requestData !is FillRequestData) {
            eventChannel.send(AutofillEvent.Abort)
            return
        }

        val formInformation = requestData.form
        when (formInformation.type) {
            is FormType.Credentials -> {
                val login = loginRepository.getLoginById(itemId) ?: run {
                    eventChannel.send(AutofillEvent.Abort)
                    return
                }

                sendLoginFillEvent(login)
            }

            is FormType.TOTP -> {
                val totpField = formInformation.fields.firstOrNull {
                    it.type == FieldType.TOTP
                } ?: run {
                    eventChannel.send(AutofillEvent.Abort)
                    return
                }

                val totp = totpRepository.getTotp(itemId) ?: run {
                    eventChannel.send(AutofillEvent.Abort)
                    return
                }

                val totpCode = totpGenerator.getTotpCode(totp).getOrNull() ?: run {
                    eventChannel.send(AutofillEvent.Abort)
                    return
                }

                val value = AutofillValue(
                    autofillId = totpField.autofillId,
                    value = totpCode.code
                )

                eventChannel.send(
                    AutofillEvent.Fill(
                        autofillDatasetProvider.getFillingDataset(
                            listOf(value)
                        )
                    )
                )
            }
        }
    }

    private suspend fun sendLoginFillEvent(login: Login) {
        val values = requestData.form.fields.mapNotNull {
            val type = it.type
            if (type !is FieldType.Credentials) return@mapNotNull null
            val value = when (type) {
                FieldType.Credentials.Password -> {
                    val pwd = login.passwordCredential ?: return@mapNotNull null
                    val wrappedVaultKey = vaultRepository.getKeyInformation(login.vaultId) ?: run {
                        eventChannel.send(AutofillEvent.Abort)
                        return
                    }

                    cryptographicScopeProvider.itemScope(
                        wrappedVaultKeyInformation = WrappedVaultKeyInformation(
                            wrappedVaultKey = wrappedVaultKey,
                            vaultId = login.vaultId,
                        ),
                        wrappedItemKeyInformation = login.wrappedItemKeyInformation(),
                    ) {
                        pwd.secret.decrypt()
                    }.getOrNull()
                }

                FieldType.Credentials.Username -> login.username

                FieldType.Credentials.EMail -> {
                    login.username?.let { potentialEmail ->
                        val isEmail = PatternsCompat.EMAIL_ADDRESS.matcher(potentialEmail).matches()
                        if (isEmail) potentialEmail else null
                    }
                }

                FieldType.Credentials.Phone -> {
                    login.username?.let { potentialPhone ->
                        val isPhone = Patterns.PHONE.matcher(potentialPhone).matches()
                        if (isPhone) potentialPhone else null
                    }
                }

                FieldType.Undefined -> null
            }

            if (value.isNullOrBlank()) return@mapNotNull null

            AutofillValue(
                autofillId = it.autofillId,
                value = value
            )
        }

        // If no values could be extracted, abort the autofill request. This may happen on multi-page
        // authentication screens. Let's say a screen only has a value for the username/email, but
        // the user selected a item that des not have a username/email set. In this case we just abort.
        if (values.isEmpty()) {
            eventChannel.send(AutofillEvent.Abort)
            return
        }

        eventChannel.send(AutofillEvent.Fill(autofillDatasetProvider.getFillingDataset(values)))
    }

    private suspend fun sendGeneratedPasswordFillEvent(password: String) {
        val values = requestData.form.fields
            .filter { it.type is FieldType.Credentials.Password }
            .map {
                AutofillValue(
                    autofillId = it.autofillId,
                    value = password
                )
            }

        if (values.isEmpty()) {
            eventChannel.send(AutofillEvent.Abort)
            return
        }

        eventChannel.send(
            AutofillEvent.Fill(
                autofillDatasetProvider.getFillingDataset(values),
                copyToClipboard = password
            )
        )
    }

    companion object {
        const val KEY_AUTOFILL_INFORMATION = "extraction"
    }
}