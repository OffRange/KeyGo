package de.davis.keygo.feature.item.core.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.davis.keygo.core.ui.clipboard.setText
import de.davis.keygo.core.ui.components.KeyGoCard
import de.davis.keygo.core.ui.components.KeyGoCardProperties
import de.davis.keygo.feature.item.core.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

fun LazyListScope.entry(
    title: String,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    trailingContent: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    item(key = title) {
        EntryCard(
            title = { Text(text = title) },
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

        var copied by remember { mutableStateOf(false) }

        LaunchedEffect(copied) {
            if (!copied) return@LaunchedEffect

            delay(CopiedFeedbackDuration)
            copied = false
        }

        val borderColor by animateColorAsState(
            targetValue = if (copied) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant,
        )
        val borderWidth by animateDpAsState(
            targetValue = if (copied) CopiedBorderWidth else IdleBorderWidth,
        )

        EntryCard(
            title = {
                AnimatedContent(targetState = copied) { isCopied ->
                    if (isCopied)
                        Text(
                            text = stringResource(R.string.copied, title),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.semantics {
                                liveRegion = LiveRegionMode.Polite
                            },
                        )
                    else
                        Text(text = title)
                }
            },
            leadingIcon = leadingIcon,
            modifier = modifier.animateItem(),
            properties = KeyGoCardProperties.outlined().copy(
                border = BorderStroke(borderWidth, borderColor),
            ),
            trailingContent = trailingContent,
            onClick = {
                scope.launch {
                    clipboard.setText(
                        label = title,
                        text = dataToCopy(),
                        sensitive = sensitive,
                    )

                    copied = true
                }
            },
            onClickLabel = stringResource(R.string.copy_entry, title),
            content = content,
        )
    }
}

@Composable
private fun EntryCard(
    title: @Composable () -> Unit,
    leadingIcon: ImageVector,
    modifier: Modifier,
    trailingContent: @Composable (() -> Unit)?,
    onClick: (() -> Unit)?,
    onClickLabel: String?,
    content: @Composable () -> Unit,
    properties: KeyGoCardProperties = KeyGoCardProperties.outlined(),
) {
    val cardLeadingItem: @Composable () -> Unit = {
        Icon(
            imageVector = leadingIcon,
            contentDescription = null,
        )
    }

    if (onClick == null)
        KeyGoCard(
            title = title,
            modifier = modifier,
            properties = properties,
            leadingItem = cardLeadingItem,
            trailingItem = trailingContent,
        ) {
            content()
        }
    else
        KeyGoCard(
            onClick = onClick,
            title = title,
            modifier = modifier,
            onClickLabel = onClickLabel,
            properties = properties,
            leadingItem = cardLeadingItem,
            trailingItem = trailingContent,
        ) {
            content()
        }
}

private val CopiedFeedbackDuration = 2.seconds
private val IdleBorderWidth = 1.dp
private val CopiedBorderWidth = 2.dp
