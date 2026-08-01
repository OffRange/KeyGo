package de.davis.keygo.feature.backup.presentation.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Reserved for permanent outcomes. A recoverable mistake gets ordinary supporting text, since a
 * warning the user learns to scroll past protects nothing.
 */
@Composable
internal fun BackupWarningCard(text: String, modifier: Modifier = Modifier) {
    ListItem(
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            leadingContentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        leadingContent = {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
            )
        },
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = text)
    }
}
