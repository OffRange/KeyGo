package de.davis.keygo.feature.password_health.presentation.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.animateBounds
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.item.presentation.StrengthIndicator
import de.davis.keygo.feature.password_health.R
import de.davis.keygo.feature.password_health.domain.model.ItemHealth
import de.davis.keygo.feature.password_health.domain.model.ItemIssue
import de.davis.keygo.feature.password_health.domain.model.PasswordFixError
import de.davis.keygo.feature.password_health.presentation.model.FixFlow
import de.davis.keygo.feature.password_health.presentation.model.PasswordHealthUiEvent
import de.davis.keygo.feature.password_health.presentation.openedSegmentContainerColor
import de.davis.keygo.feature.password_health.presentation.segmentContainerColor

@Composable
internal fun ItemHealthRow(
    itemHealth: ItemHealth,
    shapes: ListItemShapes,
    pendingFix: FixFlow.Pending?,
    isOpen: Boolean,
    onEvent: (PasswordHealthUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val breach = itemHealth.breach
    val weak = itemHealth.weak
    val username = itemHealth.username

    SegmentedListItem(
        onClick = { onEvent(PasswordHealthUiEvent.ItemClicked(itemHealth.itemId)) },
        shapes = shapes,
        modifier = modifier.fillMaxWidth(),
        colors = ListItemDefaults.segmentedColors(
            containerColor = if (isOpen) openedSegmentContainerColor else segmentContainerColor,
        ),
        supportingContent = if (pendingFix != null || username != null || weak != null) {
            {
                AnimatedContent(
                    targetState = pendingFix,
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateHeight(),
                    contentKey = { it != null },
                    transitionSpec = {
                        ContentTransform(
                            targetContentEnter = fadeIn(
                                tween(FadeInMillis, delayMillis = FadeOutMillis),
                            ),
                            initialContentExit = fadeOut(tween(FadeOutMillis)),
                            // A size transform animates width and height together, and the width
                            // it reports trails the width the row just measured the content with,
                            // so the content ends up wider than the box it is drawn in and gets
                            // clipped. Leave the width to the layout, which hands over exactly the
                            // space the trailing button has freed, and animate the height alone.
                            sizeTransform = null,
                        )
                    },
                    label = "fix-details",
                ) { pending ->
                    if (pending != null) PendingFixActions(
                        urls = itemHealth.urls,
                        pending = pending,
                        onEvent = onEvent,
                    ) else FlaggedDetails(username = username, weak = weak)
                }
            }
        } else null,
        trailingContent = {
            AnimatedVisibility(
                visible = pendingFix == null,
                enter = expandHorizontally(tween(CollapseMillis), expandFrom = Alignment.End) +
                        fadeIn(tween(FadeInMillis, delayMillis = FadeOutMillis)),
                exit = shrinkHorizontally(tween(CollapseMillis), shrinkTowards = Alignment.End) +
                        fadeOut(tween(FadeOutMillis)),
            ) {
                IconButton(
                    onClick = { onEvent(PasswordHealthUiEvent.FixClicked(itemHealth.itemId)) },
                ) {
                    Icon(imageVector = Icons.Default.AutoFixHigh, contentDescription = null)
                }
            }
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ItemHealthTitle(
            title = itemHealth.title,
            breach = breach,
            inlineBreach = pendingFix != null,
        )
    }
}

@Composable
private fun ItemHealthTitle(
    title: String,
    breach: ItemIssue.Breached?,
    inlineBreach: Boolean,
    modifier: Modifier = Modifier,
) {
    // The lookahead pass only pays off when there is a badge to move; skip it for every other row.
    if (breach == null) return Text(
        text = title,
        modifier = modifier,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )

    Box(modifier = modifier) {
        LookaheadScope {
            val badge = remember(breach) {
                movableContentOf {
                    Text(
                        text = pluralStringResource(
                            R.plurals.needs_attention_found_in_breach,
                            breach.occurrences,
                            breach.occurrences,
                        ),
                        modifier = Modifier.animateBounds(this@LookaheadScope),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Column(
                modifier = Modifier.animateBounds(this@LookaheadScope),
                verticalArrangement = Arrangement.spacedBy(TitleLineSpacing),
            ) {
                if (!inlineBreach) badge()

                Row(
                    horizontalArrangement = Arrangement.spacedBy(TitleBadgeSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .animateBounds(this@LookaheadScope),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    if (inlineBreach) badge()
                }
            }
        }
    }
}

@Composable
private fun Modifier.animateHeight(): Modifier {
    var contentHeight by remember { mutableIntStateOf(UnmeasuredHeight) }
    val animatedHeight = remember { Animatable(UnmeasuredHeight, Int.VectorConverter) }

    LaunchedEffect(contentHeight) {
        if (contentHeight == UnmeasuredHeight) return@LaunchedEffect

        if (animatedHeight.value == UnmeasuredHeight) animatedHeight.snapTo(contentHeight)
        else animatedHeight.animateTo(contentHeight, tween(ResizeMillis))
    }

    return clipToBounds()
        .layout { measurable, constraints ->
            val placeable = measurable.measure(
                constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity),
            )
            val height = animatedHeight.value

            layout(
                width = placeable.width,
                height = if (height == UnmeasuredHeight) placeable.height else height,
            ) {
                placeable.place(0, 0)
            }
        }
        .onSizeChanged { contentHeight = it.height }
}

@Composable
private fun FlaggedDetails(
    username: String?,
    weak: ItemIssue.Weak?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (username != null) Text(
            text = username,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (weak != null) StrengthIndicator(passwordScore = weak.score)
    }
}

@Composable
private fun PendingFixActions(
    urls: List<String>,
    pending: FixFlow.Pending,
    onEvent: (PasswordHealthUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = !pending.applying

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = stringResource(R.string.fix_pending_instruction))

        if (pending.error != null) Text(
            text = pending.error.message,
            color = MaterialTheme.colorScheme.error,
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextButton(
                onClick = { onEvent(PasswordHealthUiEvent.DiscardFix) },
                enabled = enabled,
            ) {
                Text(text = stringResource(R.string.fix_cancel))
            }

            if (urls.isNotEmpty()) OpenSiteAction(
                urls = urls,
                enabled = enabled,
                onOpen = { onEvent(PasswordHealthUiEvent.OpenSite(it)) },
            )

            Button(
                onClick = { onEvent(PasswordHealthUiEvent.ConfirmPasswordChanged) },
                enabled = enabled,
            ) {
                Text(text = stringResource(R.string.fix_confirm_changed))
            }
        }
    }
}

@Composable
private fun OpenSiteAction(
    urls: List<String>,
    enabled: Boolean,
    onOpen: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    val single = urls.singleOrNull()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        TextButton(
            onClick = { if (single != null) onOpen(single) else expanded = true },
            enabled = enabled,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
            Text(
                text = stringResource(R.string.fix_open_site),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (single == null) {
                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier
                        .size(ButtonDefaults.IconSize)
                        .graphicsLayer { rotationZ = chevronRotation },
                )
            }
        }

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MenuDefaults.groupStandardContainerColor,
            shape = MenuDefaults.standaloneGroupShape,
        ) {
            urls.forEachIndexed { index, url ->
                DropdownMenuItem(
                    text = { Text(text = url) },
                    onClick = {
                        expanded = false
                        onOpen(url)
                    },
                    shape = MenuDefaults.itemShape(index, urls.size).shape,
                )
            }
        }
    }
}

private const val FadeOutMillis = 90
private const val FadeInMillis = 220
private const val CollapseMillis = 150
private const val ResizeMillis = 250
private const val UnmeasuredHeight = -1

private val TitleLineSpacing = 2.dp
private val TitleBadgeSpacing = 8.dp

private val PasswordFixError.message: String
    @Composable
    get() = stringResource(
        when (this) {
            PasswordFixError.Locked -> R.string.fix_failed_locked
            PasswordFixError.Save -> R.string.fix_failed_save
        }
    )

@Preview
@Composable
private fun ItemHealthRowPreview() {
    val itemId = newItemId()

    MaterialTheme {
        Surface {
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                ItemHealthRow(
                    itemHealth = ItemHealth(
                        itemId = newItemId(),
                        title = "Old forum account",
                        username = "user@example.com",
                        issues = listOf(ItemIssue.Weak(score = PasswordScore.Weak)),
                    ),
                    shapes = ListItemDefaults.shapes(MaterialTheme.shapes.large),
                    pendingFix = null,
                    isOpen = false,
                    onEvent = {},
                )

                ItemHealthRow(
                    itemHealth = ItemHealth(
                        itemId = itemId,
                        title = "GitHub",
                        username = "user@example.com",
                        issues = listOf(ItemIssue.Breached(occurrences = 4)),
                        urls = listOf("github.com", "gist.github.com"),
                    ),
                    shapes = ListItemDefaults.shapes(MaterialTheme.shapes.large),
                    pendingFix = FixFlow.Pending(
                        itemId = itemId,
                        password = "correct-horse-battery-staple",
                    ),
                    isOpen = true,
                    onEvent = {},
                )
            }
        }
    }
}
