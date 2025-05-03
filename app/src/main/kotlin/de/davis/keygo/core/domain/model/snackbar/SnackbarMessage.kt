package de.davis.keygo.core.domain.model.snackbar

import de.davis.keygo.core.presentation.UIText

data class SnackbarMessage(
    val message: UIText,
    val action: SnackbarAction? = null,
    val onDismiss: () -> Unit = {},
)

