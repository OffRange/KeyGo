package de.davis.keygo.autofill.presentation.activity

import androidx.biometric.BiometricPrompt
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import de.davis.keygo.R
import de.davis.keygo.autofill.presentation.AutofillDatasetProvider
import de.davis.keygo.autofill.presentation.model.AutofillEvent
import de.davis.keygo.autofill.presentation.model.AutofillUiEvent
import de.davis.keygo.autofill.presentation.model.AutofillUiState
import de.davis.keygo.autofill.presentation.model.AutofillValue
import de.davis.keygo.autofill.presentation.model.FieldType
import de.davis.keygo.autofill.presentation.model.FillRequestData
import de.davis.keygo.autofill.presentation.model.Form
import de.davis.keygo.autofill.presentation.model.FormType
import de.davis.keygo.autofill.presentation.model.Request
import de.davis.keygo.autofill.presentation.model.RequestData
import de.davis.keygo.autofill.presentation.model.SaveRequestData
import de.davis.keygo.core.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.domain.crypto.decryptSecretData
import de.davis.keygo.core.domain.usecase.HasValidAccessUseCase
import de.davis.keygo.core.identity.biometric.domain.usecase.GetBiometricCryptoSetupAvailabilityUseCase
import de.davis.keygo.core.identity.biometric.domain.usecase.GetBiometricHardwareAvailabilityUseCase
import de.davis.keygo.core.identity.biometric.domain.usecase.PrepareBiometricCipherUseCase
import de.davis.keygo.core.identity.biometric.domain.usecase.UnlockWithBiometricsUseCase
import de.davis.keygo.core.identity.biometric.presentation.BiometricViewModel
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.Password
import de.davis.keygo.core.item.domain.repository.PasswordRepository
import de.davis.keygo.core.presentation.UIText
import de.davis.keygo.item.core.presentation.model.DetailPaneInformation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel


@KoinViewModel
internal class AutofillViewModel(
    savedStateHandle: SavedStateHandle,
    private val passwordRepository: PasswordRepository,
    private val cryptographicScopeProvider: CryptographicScopeProvider,
    private val autofillDatasetProvider: AutofillDatasetProvider,

    getBiometricCryptoSetupAvailability: GetBiometricCryptoSetupAvailabilityUseCase,
    getBiometricHardwareAvailability: GetBiometricHardwareAvailabilityUseCase,
    hasValidAccess: HasValidAccessUseCase,
    prepareBiometricCipher: PrepareBiometricCipherUseCase,
    unlockWithBiometrics: UnlockWithBiometricsUseCase,
) : BiometricViewModel(
    getBiometricCryptoSetupAvailability,
    getBiometricHardwareAvailability,
    hasValidAccess,
    prepareBiometricCipher,
    unlockWithBiometrics
) {

    private val requestData = savedStateHandle.get<RequestData>(KEY_AUTOFILL_INFORMATION)
        ?: throw IllegalArgumentException("Extraction must not be null")

    private val eventChannel = Channel<AutofillEvent>()
    val events = eventChannel.receiveAsFlow()

    private val _uiState = MutableStateFlow(AutofillUiState())
    val uiState = _uiState.asStateFlow()

    fun start() {
        viewModelScope.launch {
            when (requestData) {
                is SaveRequestData -> handleSaveRequest(requestData)

                is FillRequestData.Pinned,
                is FillRequestData.App -> _uiState.update { it.copy(request = Request.SelectItem) }

                is FillRequestData.Suggestion -> handleSuggestionRequest(requestData)
            }
        }
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
            url = urls.firstOrNull() ?: "",
        )
    }

    private fun handleSaveRequest(requestData: SaveRequestData) {
        viewModelScope.launch {
            _uiState.update { it.copy(request = Request.SaveItem(requestData.form.toRawItem())) }
        }
    }

    private fun handleSuggestionRequest(suggestionInfo: FillRequestData.Suggestion) {
        _uiState.update { it.copy(vaultId = suggestionInfo.vaultId) }
        viewModelScope.launch {
            when (suggestionInfo.form.type) {
                is FormType.Credentials -> handleSuggestPasswordRequest(suggestionInfo)
            }
        }
    }

    private suspend fun handleSuggestPasswordRequest(suggestionInfo: FillRequestData.Suggestion) {
        // TODO: only fetch name of the item here, or make it generic for all item types
        val item = getPasswordById(suggestionInfo.vaultId)
            ?: throw IllegalArgumentException("Password for vaultId=${suggestionInfo.vaultId} not found")
        requestBiometricAuthentication(
            title = UIText.ResourceString(R.string.unlock_item, item.name),
            negativeButton = UIText.ResourceString(R.string.password)
        )
    }

    override fun onBiometricFailed(errorCode: Int, errString: String) {
        super.onBiometricFailed(errorCode, errString)
        if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
            viewModelScope.launch {
                eventChannel.send(AutofillEvent.Abort)
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(request = Request.JustAuthenticateWithPwd) }
        }
    }

    override fun onUnlocked() {
        viewModelScope.launch {
            if (requestData is FillRequestData.Suggestion) {
                sendFillEvent(requestData.vaultId)
                return@launch
            }
        }
    }

    private fun onItemSelected(vaultId: ItemId) {
        viewModelScope.launch {
            _uiState.update { it.copy(showAssociationDialog = true, vaultId = vaultId) }
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
        }
    }

    private fun associateItem() {
        val vaultId = uiState.value.vaultId
        hideAssociationDialog()
    }

    private fun hideAssociationDialog() {
        _uiState.update { it.copy(showAssociationDialog = false) }
        viewModelScope.launch {
            sendFillEvent(_uiState.value.vaultId)
        }
    }

    private suspend fun getPasswordById(vaultId: ItemId): Password? {
        return passwordRepository.getPasswordById(vaultId)
    }

    private suspend fun sendFillEvent(vaultId: ItemId) {
        if (requestData !is FillRequestData) {
            eventChannel.send(AutofillEvent.Abort)
            return
        }

        val formInformation = requestData.form
        when (formInformation.type) {
            is FormType.Credentials -> {
                val password = getPasswordById(vaultId) ?: run {
                    eventChannel.send(AutofillEvent.Abort)
                    return
                }

                sendPasswordFillEvent(password)
            }
        }
    }

    private suspend fun sendPasswordFillEvent(password: Password) {
        val values = requestData.form.fields.mapNotNull {
            val value = when (it.type) {
                FieldType.Credentials.Password -> cryptographicScopeProvider.scope {
                    password.encryptedData.decryptSecretData()
                }

                FieldType.Credentials.Username -> password.username

                // TODO: add support for these field types
                FieldType.Credentials.EMail,
                FieldType.Credentials.Phone,
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