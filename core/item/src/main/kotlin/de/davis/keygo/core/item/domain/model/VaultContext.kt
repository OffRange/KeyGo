package de.davis.keygo.core.item.domain.model

import de.davis.keygo.core.item.domain.alias.VaultId

sealed interface VaultContext {
    data object NoSpecific : VaultContext
    data class ById(val vaultId: VaultId) : VaultContext
}

fun VaultContext.getIdOrNull(): VaultId? = when (this) {
    VaultContext.NoSpecific -> null
    is VaultContext.ById -> vaultId
}