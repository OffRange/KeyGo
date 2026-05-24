package de.davis.keygo.feature.item.create.presentation.model

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Stable
import de.davis.keygo.core.item.domain.model.Tag

/**
 * State shared by every item create/edit screen: the name and notes fields every item has, the selectable
 * vaults, and the tags currently assigned to the item alongside the remaining tags offered as
 * suggestions. Owned by [de.davis.keygo.feature.item.create.presentation.ItemViewModel] and paired
 * with each screen's own item state in [ItemUiState.Ready].
 */
@Stable
internal data class SharedItemState(
    val nameTextFieldState: TextFieldState,
    val notesTextFieldState: TextFieldState,
    val nameExists: Boolean,
    val vaultsState: VaultsState,
    val itemAssignedTags: Set<Tag>,
    val tagsForSuggestion: Set<Tag>,
)
