package de.davis.keygo.autofill.presentation.activity

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import de.davis.keygo.R
import de.davis.keygo.autofill.presentation.AutofillDatasetProvider
import de.davis.keygo.autofill.presentation.model.AutofillEvent
import de.davis.keygo.autofill.presentation.model.AutofillInformation
import de.davis.keygo.autofill.presentation.model.AutofillValue
import de.davis.keygo.autofill.presentation.model.FieldType
import de.davis.keygo.core.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.domain.model.Password
import de.davis.keygo.core.domain.repository.PasswordRepository
import de.davis.keygo.core.domain.usecase.HasValidAccessUseCase
import de.davis.keygo.core.identity.biometric.domain.usecase.GetBiometricCryptoSetupAvailabilityUseCase
import de.davis.keygo.core.identity.biometric.domain.usecase.GetBiometricHardwareAvailabilityUseCase
import de.davis.keygo.core.identity.biometric.domain.usecase.PrepareBiometricCipherUseCase
import de.davis.keygo.core.identity.biometric.domain.usecase.UnlockWithBiometricsUseCase
import de.davis.keygo.core.identity.biometric.presentation.BiometricViewModel
import de.davis.keygo.core.presentation.UIText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
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

    private val autofillInformation =
        savedStateHandle.get<AutofillInformation>(KEY_AUTOFILL_INFORMATION)
            ?: throw IllegalArgumentException("Extraction must not be null")

    private val eventChannel = Channel<AutofillEvent>()
    val events = eventChannel.receiveAsFlow()

    private lateinit var password: Password

    fun start() {
        viewModelScope.launch {
            when (autofillInformation) {
                is AutofillInformation.App -> {
                    //TODO implement app autofill
                    eventChannel.send(AutofillEvent.Abort)
                }

                is AutofillInformation.Suggestion -> handleSuggestionRequest(autofillInformation)
            }
        }
    }

    private fun handleSuggestionRequest(suggestionInfo: AutofillInformation.Suggestion) {
        viewModelScope.launch {
            password = passwordRepository.getVaultPasswordById(suggestionInfo.vaultId)
                ?: throw IllegalArgumentException("Password for vaultId=${suggestionInfo.vaultId} not found")
            requestBiometricAuthentication(
                title = UIText.ResourceString(R.string.unlock_item, password.name)
            )
        }
    }

    override fun onBiometricFailed(errorCode: Int, errString: String) {
        super.onBiometricFailed(errorCode, errString)
        viewModelScope.launch {
            eventChannel.send(AutofillEvent.Abort)
        }
    }

    override fun onUnlocked() {
        viewModelScope.launch {
            val values = autofillInformation.extraction.fields.mapNotNull {
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

            eventChannel.send(AutofillEvent.Fill(autofillDatasetProvider.getFillingDataset(values)))
        }
    }

    companion object {
        const val KEY_AUTOFILL_INFORMATION = "extraction"
    }
}