package de.davis.keygo.autofill.presentation.activity

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import de.davis.keygo.R
import de.davis.keygo.autofill.presentation.AutofillDatasetProvider
import de.davis.keygo.autofill.presentation.model.AutofillEvent
import de.davis.keygo.autofill.presentation.model.AutofillValue
import de.davis.keygo.autofill.presentation.model.FieldType
import de.davis.keygo.autofill.presentation.model.FillRequestData
import de.davis.keygo.autofill.presentation.model.Form
import de.davis.keygo.autofill.presentation.model.FormType
import de.davis.keygo.autofill.presentation.model.Request
import de.davis.keygo.autofill.presentation.model.RequestData
import de.davis.keygo.autofill.presentation.model.SaveRequestData
import de.davis.keygo.core.domain.alias.ItemId
import de.davis.keygo.core.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.domain.model.Password
import de.davis.keygo.core.domain.repository.PasswordRepository
import de.davis.keygo.core.domain.usecase.HasValidAccessUseCase
import de.davis.keygo.core.identity.biometric.domain.usecase.GetBiometricCryptoSetupAvailabilityUseCase
import de.davis.keygo.core.identity.biometric.domain.usecase.GetBiometricHardwareAvailabilityUseCase
import de.davis.keygo.core.identity.biometric.domain.usecase.PrepareBiometricCipherUseCase
import de.davis.keygo.core.identity.biometric.domain.usecase.UnlockWithBiometricsUseCase
import de.davis.keygo.core.identity.biometric.presentation.BiometricViewModel
import de.davis.keygo.core.identity.biometric.presentation.model.BiometricRequest
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

    private val _request = MutableStateFlow<Request<*>>(Request.None)
    val request = _request.asStateFlow()

    fun start() {
        viewModelScope.launch {
            when (requestData) {
                is SaveRequestData -> handleSaveRequest(requestData)

                is FillRequestData.Pinned,
                is FillRequestData.App -> _request.update { Request.SelectItem }

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
            _request.update { Request.SaveItem(requestData.form.toRawItem()) }
        }
    }

    private fun handleSuggestionRequest(suggestionInfo: FillRequestData.Suggestion) {
        viewModelScope.launch {
            when (suggestionInfo.form.type) {
                is FormType.Credentials -> handleSuggestPasswordRequest(suggestionInfo)
            }
        }
    }

    private suspend fun handleSuggestPasswordRequest(suggestionInfo: FillRequestData.Suggestion) {
        val password = getPasswordById(suggestionInfo.vaultId)
            ?: throw IllegalArgumentException("Password for vaultId=${suggestionInfo.vaultId} not found")
        requestBiometricAuthentication(
            title = UIText.ResourceString(R.string.unlock_item, password.name),
            reason = BiometricRequest.Reason.UnlockItem(password)
        )
    }

    override fun onBiometricFailed(errorCode: Int, errString: String) {
        super.onBiometricFailed(errorCode, errString)
        viewModelScope.launch {
            eventChannel.send(AutofillEvent.Abort)
        }
    }

    override fun onUnlocked(requestReason: BiometricRequest.Reason) {
        if (requestReason !is BiometricRequest.Reason.UnlockItem) return
        if (requestReason.vaultItem !is Password) return
        val password = requestReason.vaultItem

        viewModelScope.launch {
            sendPasswordFillEvent(password)
        }
    }

    fun onItemSelected(vaultId: ItemId) {
        viewModelScope.launch {
            // TODO: check if id is actually a password, and if the form actually represents credentials
            val password = getPasswordById(vaultId) ?: return@launch
            sendPasswordFillEvent(password)
        }
    }

    private suspend fun getPasswordById(vaultId: ItemId): Password? {
        return passwordRepository.getVaultPasswordById(vaultId)
    }

    private suspend fun sendPasswordFillEvent(password: Password) {
        if (requestData !is FillRequestData) {
            eventChannel.send(AutofillEvent.Abort)
            return
        }

        val formInformation = requestData.form
        if (formInformation.type !is FormType.Credentials) {
            eventChannel.send(AutofillEvent.Abort)
            return
        }

        val values = requestData.form.fields.mapNotNull {
            val value = when (it.type) {
                FieldType.Credentials.Password -> cryptographicScopeProvider.scope {
                    password.encryptedData.decrypt().decodeToString()
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