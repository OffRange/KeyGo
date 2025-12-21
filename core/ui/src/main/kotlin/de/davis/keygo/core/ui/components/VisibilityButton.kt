package de.davis.keygo.core.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import de.davis.keygo.core.ui.R

@Composable
fun VisibilityButton(isHidden: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
    ) {
        val icon =
            if (isHidden) Icons.Default.Visibility
            else Icons.Default.VisibilityOff

        val description =
            if (isHidden) R.string.show_password_content_description
            else R.string.hide_password_content_description
        Icon(
            imageVector = icon,
            contentDescription = stringResource(id = description)
        )
    }
}