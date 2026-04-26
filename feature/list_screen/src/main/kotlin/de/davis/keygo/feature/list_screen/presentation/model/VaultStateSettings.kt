package de.davis.keygo.feature.list_screen.presentation.model

import de.davis.keygo.feature.list_screen.domain.model.VaultCreationError

internal data class VaultStateSettings(
    val showSelection: Boolean = false,
    val showCreationDialog: Boolean = false,
    val error: VaultCreationError? = null,
)
