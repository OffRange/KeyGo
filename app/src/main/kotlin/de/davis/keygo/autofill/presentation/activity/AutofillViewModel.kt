package de.davis.keygo.autofill.presentation.activity

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.autofill.presentation.AutofillDatasetProvider
import de.davis.keygo.autofill.presentation.model.AutofillEvent
import de.davis.keygo.autofill.presentation.model.AutofillInformation
import de.davis.keygo.autofill.presentation.model.AutofillValue
import de.davis.keygo.autofill.presentation.model.FieldType
import de.davis.keygo.core.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.domain.repository.PasswordRepository
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
) : ViewModel() {

    private val autofillInformation =
        savedStateHandle.get<AutofillInformation>(KEY_AUTOFILL_INFORMATION)
            ?: throw IllegalArgumentException("Extraction must not be null")

    private val eventChannel = Channel<AutofillEvent>()
    val events = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            val password = passwordRepository.getVaultPasswordById(autofillInformation.vaultId)
                ?: throw IllegalArgumentException("Password for vaultId=${autofillInformation.vaultId} not found")

            val values = autofillInformation.extraction.fields.mapNotNull {
                val value = when (it.type) {
                    FieldType.Credentials.EMail -> password.username
                    FieldType.Credentials.Password -> cryptographicScopeProvider.scope {
                        password.encryptedData.decrypt().decodeToString()
                    }

                    FieldType.Credentials.Phone -> password.username
                    FieldType.Credentials.Username -> password.username
                    FieldType.Undefined -> return@mapNotNull null
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