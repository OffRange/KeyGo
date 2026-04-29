package de.davis.keygo.feature.list_screen.presentation.model

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Stable
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.model.Vault
import de.davis.keygo.core.item.domain.model.VaultContext
import de.davis.keygo.core.item.domain.model.VaultMetadata
import de.davis.keygo.feature.list_screen.domain.model.VaultCreationError

@Stable
internal sealed interface VaultState {

    data object Closed : VaultState

    @Stable
    data class Select(
        val vaults: List<VaultMetadata> = emptyList(),
        val vaultContext: VaultContext = VaultContext.NoSpecific,
    ) : VaultState {
        val sumCount = vaults.sumOf { it.count }
        val hasMultipleVaults = vaults.size > 1
    }

    @Stable
    sealed interface CreateOrUpdate : VaultState {
        val nameTextFieldState: TextFieldState
        val icon: Vault.Icon
        val error: VaultCreationError?
    }

    @Stable
    data class Create(
        override val nameTextFieldState: TextFieldState = TextFieldState(),
        override val icon: Vault.Icon = Vault.Icon.Default,
        override val error: VaultCreationError? = null,
    ) : CreateOrUpdate

    @Stable
    data class Edit(
        val vaultId: VaultId,
        override val nameTextFieldState: TextFieldState = TextFieldState(),
        override val icon: Vault.Icon = Vault.Icon.Default,
        override val error: VaultCreationError? = null,
    ) : CreateOrUpdate
}
