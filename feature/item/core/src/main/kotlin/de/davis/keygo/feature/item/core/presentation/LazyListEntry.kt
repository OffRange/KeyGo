package de.davis.keygo.feature.item.core.presentation

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import de.davis.keygo.core.ui.components.KeyGoCard

fun LazyListScope.entry(
    title: String,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    trailingContent: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    item(key = title) {
        KeyGoCard(
            title = {
                Text(text = title)
            },
            leadingItem = {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                )
            },
            trailingItem = trailingContent,
            modifier = modifier.animateItem(),
        ) {
            content()
        }
    }
}
