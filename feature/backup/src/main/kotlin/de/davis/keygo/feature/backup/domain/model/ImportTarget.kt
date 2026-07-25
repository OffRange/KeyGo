package de.davis.keygo.feature.backup.domain.model

import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.model.Vault

/**
 * Where an import should put its items.
 *
 * A `null` target (the absence of this type) means "use the vaults described by the backup", which
 * is what JSON restores do: their vault names are real and worth preserving. CSV imports always
 * carry a target, because the vault name in a parsed CSV backup is a placeholder the parser invents.
 */
sealed interface ImportTarget {

    /** Put everything in a vault that already exists. */
    data class Existing(val vaultId: VaultId) : ImportTarget

    /**
     * Create a vault named [name] with [icon] and put everything in it. Always creates, even when a
     * vault of this name already exists: the user was shown the existing vaults and chose to make a
     * new one.
     */
    data class New(
        val name: String,
        val icon: Vault.Icon = Vault.Icon.Default,
    ) : ImportTarget
}
