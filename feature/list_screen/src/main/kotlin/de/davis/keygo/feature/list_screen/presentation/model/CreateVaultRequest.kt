package de.davis.keygo.feature.list_screen.presentation.model

import de.davis.keygo.core.item.domain.model.Vault

internal data class CreateVaultRequest(
    val name: String,
    val icon: Vault.Icon
)
