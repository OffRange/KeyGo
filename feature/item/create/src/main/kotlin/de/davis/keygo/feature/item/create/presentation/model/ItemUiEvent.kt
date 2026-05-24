package de.davis.keygo.feature.item.create.presentation.model

import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.model.Tag

internal interface ItemUiEvent {

    data object OnSubmit : ItemUiEvent
    data object OnBackClick : ItemUiEvent

    data class OnRemoveTag(val tag: Tag) : ItemUiEvent
    data class OnAddTags(val tags: Set<Tag>) : ItemUiEvent

    data class OnVaultSelected(val vaultId: VaultId) : ItemUiEvent
}
