package de.davis.keygo.feature.password_health.presentation.model

import de.davis.keygo.core.item.domain.alias.ItemId

internal sealed interface PasswordHealthEvent {

    data class OpenItem(val itemId: ItemId) : PasswordHealthEvent
}
