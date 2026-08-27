package de.davis.keygo.feature.item.core.presentation

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import de.davis.keygo.core.ui.clipboard.setText
import de.davis.keygo.core.ui.components.KeyGoCard
import de.davis.keygo.feature.item.core.R
import kotlinx.coroutines.launch

fun LazyListScope.entry(
    title: String,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    trailingContent: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    item(key = title) {
        EntryCard(
            title = title,
            leadingIcon = leadingIcon,
            modifier = modifier.animateItem(),
            trailingContent = trailingContent,
            onClick = null,
            onClickLabel = null,
            content = content,
        )
    }
}

fun LazyListScope.copyableEntry(
    title: String,
    leadingIcon: ImageVector,
    dataToCopy: () -> String,
    sensitive: Boolean = false,
    modifier: Modifier = Modifier,
    trailingContent: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    item(key = title) {
        val scope = rememberCoroutineScope()
        val clipboard = LocalClipboard.current
        val context = LocalContext.current
        val copiedMessage = stringResource(R.string.copied, title)

        EntryCard(
            title = title,
            leadingIcon = leadingIcon,
            modifier = modifier.animateItem(),
            trailingContent = trailingContent,
            onClick = {
                scope.launch {
                    clipboard.setText(
                        label = title,
                        text = dataToCopy(),
                        sensitive = sensitive,
                    )

                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
                        Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                }
            },
            onClickLabel = stringResource(R.string.copy_entry, title),
            content = content,
        )
    }
}

@Composable
private fun EntryCard(
    title: String,
    leadingIcon: ImageVector,
    modifier: Modifier,
    trailingContent: @Composable (() -> Unit)?,
    onClick: (() -> Unit)?,
    onClickLabel: String?,
    content: @Composable () -> Unit,
) {
    val cardTitle: @Composable () -> Unit = { Text(text = title) }
    val cardLeadingItem: @Composable () -> Unit = {
        Icon(
            imageVector = leadingIcon,
            contentDescription = null,
        )
    }

    if (onClick == null)
        KeyGoCard(
            title = cardTitle,
            modifier = modifier,
            leadingItem = cardLeadingItem,
            trailingItem = trailingContent,
        ) {
            content()
        }
    else
        KeyGoCard(
            onClick = onClick,
            title = cardTitle,
            modifier = modifier,
            onClickLabel = onClickLabel,
            leadingItem = cardLeadingItem,
            trailingItem = trailingContent,
        ) {
            content()
        }
}
