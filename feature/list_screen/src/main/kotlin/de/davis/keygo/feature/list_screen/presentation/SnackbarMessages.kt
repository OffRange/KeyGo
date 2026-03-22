package de.davis.keygo.feature.list_screen.presentation

import de.davis.keygo.core.util.domain.model.snackbar.SnackbarAction
import de.davis.keygo.core.util.domain.model.snackbar.SnackbarMessage
import de.davis.keygo.core.util.presentation.UIText
import de.davis.keygo.feature.list_screen.R

@Suppress("FunctionName")
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