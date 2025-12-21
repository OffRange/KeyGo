package de.davis.keygo.core.util.domain.model.snackbar

import de.davis.keygo.core.util.presentation.UIText

data class SnackbarAction(
    val label: UIText,
    val onClick: () -> Unit,
)