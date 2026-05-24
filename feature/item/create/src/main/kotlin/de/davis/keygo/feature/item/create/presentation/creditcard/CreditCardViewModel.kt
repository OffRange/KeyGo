package de.davis.keygo.feature.item.create.presentation.creditcard

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.feature.item.create.presentation.creditcard.model.CreditCardUiEvent
import de.davis.keygo.feature.item.create.presentation.creditcard.model.CreditCardUiState
import de.davis.keygo.feature.item.create.presentation.model.VaultsState
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class CreditCardViewModel : ViewModel() {

    private val nameTextFieldState = TextFieldState()
    private val notesTextFieldState = TextFieldState()
    private val ccHolderTextFieldState = TextFieldState()
    private val ccNumberTextFieldState = TextFieldState()
    private val ccCVVTextFieldState = TextFieldState()
    private val ccExpirationDateTextFieldState = TextFieldState()

    val state = MutableStateFlow(
        CreditCardUiState(
            nameTextFieldState = nameTextFieldState,
            notesTextFieldState = notesTextFieldState,
            ccHolderTextFieldState = ccHolderTextFieldState,
            ccNumberTextFieldState = ccNumberTextFieldState,
            ccCVVTextFieldState = ccCVVTextFieldState,
            ccExpirationDateTextFieldState = ccExpirationDateTextFieldState,
            vaultsState = VaultsState(
                vaults = emptyList(),
                selectedVaultId = newVaultId()
            )
        )
    )

    fun onEvent(event: CreditCardUiEvent) {
        when (event) {
            is CreditCardUiEvent.ItemUi -> {}
        }
    }
}