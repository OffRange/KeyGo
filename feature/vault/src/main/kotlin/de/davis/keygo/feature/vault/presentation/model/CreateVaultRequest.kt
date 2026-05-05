package de.davis.keygo.feature.vault.presentation.model

import de.davis.keygo.core.item.domain.model.Vault

data class CreateVaultRequest(
    val name: String,
    val icon: Vault.Icon
)
