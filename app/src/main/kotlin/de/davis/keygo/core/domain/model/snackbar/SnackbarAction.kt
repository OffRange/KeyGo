package de.davis.keygo.core.domain.model.snackbar

import de.davis.keygo.core.presentation.UIText

data class SnackbarAction(
    val label: UIText,
    val onClick: () -> Unit,
)