package de.davis.keygo.core.item.domain.alias

import java.util.UUID

typealias VaultId = UUID

fun newVaultId(): VaultId = UUID.randomUUID()
