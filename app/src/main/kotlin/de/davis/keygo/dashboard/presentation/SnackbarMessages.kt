package de.davis.keygo.dashboard.presentation

import de.davis.keygo.R
import de.davis.keygo.core.util.domain.model.snackbar.SnackbarAction
import de.davis.keygo.core.util.domain.model.snackbar.SnackbarMessage
import de.davis.keygo.core.util.presentation.UIText

fun ItemDeletedMessage(
    onClick: () -> Unit,
    onDismiss: () -> Unit,
) = SnackbarMessage(
    message = UIText.PluralsString(R.plurals.items_deleted, 1),
    action = SnackbarAction(
        label = UIText.ResourceString(R.string.undo),
        onClick = onClick,
    ),
    onDismiss = onDismiss
)