package de.davis.keygo.feature.credentials.presentation.create.activity

import de.davis.keygo.core.item.domain.alias.ItemId

internal sealed interface CreatePasskeyEvent {
    data object Abort : CreatePasskeyEvent
    data object ShowList : CreatePasskeyEvent
    data class OpenConfirmationDialog(
        val itemId: ItemId,
        val itemName: String,
        val rp: String
    ) : CreatePasskeyEvent

    data class Finish(val responseJson: String) : CreatePasskeyEvent
}
