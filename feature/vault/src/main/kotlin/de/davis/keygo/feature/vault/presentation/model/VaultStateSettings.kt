package de.davis.keygo.feature.vault.presentation.model

import de.davis.keygo.feature.vault.domain.model.VaultCreationError

data class VaultStateSettings(
    val showSelection: Boolean = false,
    val showCreationDialog: Boolean = false,
    val error: VaultCreationError? = null,
)
