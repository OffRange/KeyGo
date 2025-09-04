package de.davis.keygo.item.core.presentation.model

import de.davis.keygo.core.domain.model.VaultItem

sealed interface DetailPaneInformation {
    data class InitByDetailType(val detailType: DetailType) : DetailPaneInformation
    data class CreateRaw(val vaultItem: VaultItem) : DetailPaneInformation
}