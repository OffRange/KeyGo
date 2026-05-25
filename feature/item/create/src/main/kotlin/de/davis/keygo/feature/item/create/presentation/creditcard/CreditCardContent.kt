package de.davis.keygo.feature.item.create.presentation.creditcard

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.davis.keygo.core.item.domain.model.Tag
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.feature.item.core.presentation.component.CreateOrModifyItemTopAppBar
import de.davis.keygo.feature.item.core.presentation.component.KeyGoFormField
import de.davis.keygo.feature.item.core.presentation.component.gatherPendingItems
import de.davis.keygo.feature.item.core.presentation.transformation.rememberCardFormatter
import de.davis.keygo.feature.item.create.R
import de.davis.keygo.feature.item.create.presentation.component.FormGroup
import de.davis.keygo.feature.item.create.presentation.component.ItemContentWrapper
import de.davis.keygo.feature.item.create.presentation.component.KeyGoItemForm
import de.davis.keygo.feature.item.create.presentation.component.TAG_DELIMITERS
import de.davis.keygo.feature.item.create.presentation.creditcard.model.CreditCardBaseState
import de.davis.keygo.feature.item.create.presentation.creditcard.model.CreditCardUiEvent
import de.davis.keygo.feature.item.create.presentation.creditcard.model.CreditCardUiState
import de.davis.keygo.feature.item.create.presentation.model.ItemUiEvent
import de.davis.keygo.feature.item.create.presentation.model.SharedItemState
import de.davis.keygo.feature.item.core.R as ItemCoreR


@Composable
internal fun CreditCardContent(state: CreditCardUiState, onEvent: (CreditCardUiEvent) -> Unit) {
    ItemContentWrapper(
        itemType = VaultItemType.CreditCard,
        state = state,
        onBackClick = { onEvent(CreditCardUiEvent.ItemUi(ItemUiEvent.OnBackClick)) },
    ) { state ->
        CreditCardReadyContent(
            state = state.base,
            shared = state.shared,
            onEvent = onEvent,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreditCardReadyContent(
    state: CreditCardBaseState,
    shared: SharedItemState,
    onEvent: (CreditCardUiEvent) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val tagsTextFieldState = rememberTextFieldState()
    val ccNumberFormatter = rememberCardFormatter()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CreateOrModifyItemTopAppBar(
                itemType = VaultItemType.CreditCard,
                updating = state.updating,
                onBackClick = { onEvent(CreditCardUiEvent.ItemUi(ItemUiEvent.OnBackClick)) },
                actions = {
                    IconButton(
                        onClick = {
                            tagsTextFieldState.gatherPendingItems(TAG_DELIMITERS) { strings ->
                                onEvent(
                                    CreditCardUiEvent.ItemUi(
                                        ItemUiEvent.OnAddTags(
                                            strings.mapNotNullTo(mutableSetOf(), Tag::of),
                                        )
                                    ),
                                )
                            }

                            onEvent(CreditCardUiEvent.ItemUi(ItemUiEvent.OnSubmit))
                        },
                        enabled = state.canSave(shared.nameTextFieldState.text),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Done,
                            contentDescription = stringResource(R.string.submit_content_description),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        KeyGoItemForm(
            nameTextFieldState = shared.nameTextFieldState,
            tagsTextFieldState = tagsTextFieldState,
            notesTextFieldState = shared.notesTextFieldState,
            nameExists = shared.nameExists,
            vaultsState = shared.vaultsState,
            onVaultSelect = { onEvent(CreditCardUiEvent.ItemUi(ItemUiEvent.OnVaultSelected(it))) },
            assignedTags = shared.itemAssignedTags,
            tagsForSuggestions = shared.tagsForSuggestion,
            onTagSubmitted = { onEvent(CreditCardUiEvent.ItemUi(ItemUiEvent.OnAddTags(it))) },
            onDeleteTag = { onEvent(CreditCardUiEvent.ItemUi(ItemUiEvent.OnRemoveTag(it))) },
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .padding(8.dp)
                .imePadding()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            item(key = "cc_information") {
                FormGroup(
                    title = stringResource(R.string.cc_information),
                ) {
                    KeyGoFormField(
                        state = state.ccHolderTextFieldState,
                        label = { Text(text = stringResource(ItemCoreR.string.cc_holder)) }
                    )

                    KeyGoFormField(
                        state = state.ccNumberTextFieldState,
                        label = { Text(text = stringResource(ItemCoreR.string.cc_number)) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword
                        ),
                        inputTransformation = state.numberInputTransformation,
                        outputTransformation = ccNumberFormatter,
                    )

                    KeyGoFormField(
                        state = state.ccCVVTextFieldState,
                        label = { Text(text = stringResource(ItemCoreR.string.cc_cvv)) },
                        isSecure = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword
                        ),
                    )

                    KeyGoFormField(
                        state = state.ccExpirationDateTextFieldState,
                        label = { Text(text = stringResource(ItemCoreR.string.cc_expiration_date)) },
                        placeholder = { Text(text = stringResource(ItemCoreR.string.cc_expiration_date_placeholder)) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        )
                    )
                }
            }
        }
    }
}
