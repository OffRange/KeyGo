package de.davis.keygo.feature.autofill.presentation.activity

import android.util.Patterns
import androidx.core.util.PatternsCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.crypto.decryptSecretData
import de.davis.keygo.core.item.domain.model.Password
import de.davis.keygo.core.item.domain.repository.PasswordRepository
import de.davis.keygo.core.item.domain.repository.VaultItemRepository
import de.davis.keygo.core.security.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.feature.autofill.domain.usecase.AddRegistrableDomainsToPasswordUseCase
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
import de.davis.keygo.feature.item.core.presentation.model.DetailPaneInformation
import de.davis.keygo.feature.totp.domain.repository.TotpGenerator
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class AutofillViewModel(
    savedStateHandle: SavedStateHandle,
    private val passwordRepository: PasswordRepository,
    private val vaultItemRepository: VaultItemRepository,
    private val cryptographicScopeProvider: CryptographicScopeProvider,
    private val autofillDatasetProvider: AutofillDatasetProvider,
    private val doesItemHaveDomainReferences: DoesItemHaveDomainReferencesUseCase,
    private val addRegistrableDomainToPassword: AddRegistrableDomainsToPasswordUseCase,
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


    fun start() {
        handleRequestData()
    }

    private fun Form.toRawItem() = when (type) {
        // TODO: maybe find suitable names
        is FormType.Credentials -> DetailPaneInformation.CreateRaw.Password(
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

                is FillRequestData.Suggestion -> handleSuggestionRequest(requestData)
            }
        }

    private suspend fun handleSuggestionRequest(suggestionInfo: FillRequestData.Suggestion) {
        _uiState.update { it.copy(vaultId = suggestionInfo.vaultId) }

        val itemName = vaultItemRepository.getItemName(suggestionInfo.vaultId)
            ?: throw IllegalArgumentException("Name for vaultId=${suggestionInfo.vaultId} not found")

        biometricChannel.send(AutofillBiometricRequest.UnlockItem(itemName))
    }

    fun onBiometricLoginFailed() {
        viewModelScope.launch {
            _uiState.update { it.copy(request = Request.JustAuthenticateWithPwd) }
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

            val itemName = vaultItemRepository.getItemName(vaultId)
                ?: throw IllegalArgumentException("Name for vaultId=$vaultId not found")

            _uiState.update {
                it.copy(
                    associationDialogVisibility = AssociationDialogVisibility.Visible(
                        itemName = itemName,
                        domain = requestData.form.url
                    ),
                    vaultId = vaultId
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
        }
    }

    private fun associateItem() {
        val vaultItemId = uiState.value.vaultId
        requestData.form.url?.let {
            viewModelScope.launch {
                addRegistrableDomainToPassword(
                    vaultItemId = vaultItemId,
                    domain = it
                )
            }
        }
        hideAssociationDialog()
    }

    private fun hideAssociationDialog() {
        _uiState.update { it.copy(associationDialogVisibility = AssociationDialogVisibility.Hidden) }
        viewModelScope.launch {
            sendFillEvent(_uiState.value.vaultId)
        }
    }

    private suspend fun sendFillEvent(vaultId: ItemId) {
        if (requestData !is FillRequestData) {
            eventChannel.send(AutofillEvent.Abort)
            return
        }

        val formInformation = requestData.form
        when (formInformation.type) {
            is FormType.Credentials -> {
                val password = passwordRepository.getPasswordById(vaultId) ?: run {
                    eventChannel.send(AutofillEvent.Abort)
                    return
                }

                sendPasswordFillEvent(password)
            }

            is FormType.TOTP -> {
                val totpField = formInformation.fields.firstOrNull {
                    it.type == FieldType.TOTP
                } ?: run {
                    eventChannel.send(AutofillEvent.Abort)
                    return
                }

                val password = passwordRepository.getPasswordById(vaultId) ?: run {
                    eventChannel.send(AutofillEvent.Abort)
                    return
                }

                val totp = password.totpSecret?.let {
                    val secret = cryptographicScopeProvider.scope {
                        it.decryptSecretData().encodeToByteArray()
                    }
                    totpGenerator.observeTotp(secret).first()
                } ?: run {
                    eventChannel.send(AutofillEvent.Abort)
                    return
                }

                val value = AutofillValue(
                    autofillId = totpField.autofillId,
                    value = totp.code
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

    private suspend fun sendPasswordFillEvent(password: Password) {
        val values = requestData.form.fields.mapNotNull {
            val type = it.type
            if (type !is FieldType.Credentials) return@mapNotNull null
            val value = when (type) {
                FieldType.Credentials.Password -> cryptographicScopeProvider.scope {
                    password.encryptedData.decryptSecretData()
                }

                FieldType.Credentials.Username -> password.username

                FieldType.Credentials.EMail -> {
                    password.username?.let { potentialEmail ->
                        val isEmail = PatternsCompat.EMAIL_ADDRESS.matcher(potentialEmail).matches()
                        if (isEmail) potentialEmail else null
                    }
                }

                FieldType.Credentials.Phone -> {
                    password.username?.let { potentialPhone ->
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

    companion object {
        const val KEY_AUTOFILL_INFORMATION = "extraction"
    }
}