package de.davis.keygo.core.ui.components

import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun VaultItem(
    headlineContent: @Composable () -> Unit,
    supportingContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leadingContent: @Composable (() -> Unit)? = null,
    cardColors: CardColors = CardDefaults.cardColors(),
) {
    Card(modifier = modifier, colors = cardColors) {
        ListItem(
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent,
                headlineColor = LocalContentColor.current,
                supportingColor = LocalContentColor.current,
                leadingIconColor = LocalContentColor.current,
                trailingIconColor = LocalContentColor.current,
                disabledHeadlineColor = cardColors.disabledContentColor,
                disabledLeadingIconColor = cardColors.disabledContentColor,
                disabledTrailingIconColor = cardColors.disabledContentColor,
            ),
            headlineContent = headlineContent,
            supportingContent = supportingContent,
            leadingContent = leadingContent
        )
    }
}