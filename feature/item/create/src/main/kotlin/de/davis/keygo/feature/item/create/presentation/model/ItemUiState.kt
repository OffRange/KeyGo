package de.davis.keygo.feature.item.create.presentation.model

import androidx.compose.runtime.Stable

/**
 * UI state common to every item create/edit screen, assembled by [de.davis.keygo.feature.item.create.presentation.ItemViewModel].
 * The item-specific form state [S] is paired with the [SharedItemState].
 */
@Stable
internal sealed interface ItemUiState<out S> {
    data object Loading : ItemUiState<Nothing>

    @Stable
    data class Ready<S>(
        val base: S,
        val shared: SharedItemState,
    ) : ItemUiState<S>
}
