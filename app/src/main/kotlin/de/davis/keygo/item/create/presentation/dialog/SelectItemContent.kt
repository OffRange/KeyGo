package de.davis.keygo.item.create.presentation.dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.davis.keygo.R
import de.davis.keygo.generated.item.VaultItemType
import de.davis.keygo.generated.item.getString

@Composable
fun SelectItemContent(onSelect: (VaultItemType) -> Unit) {
    AlertDialogContent(
        title = {
            Text(text = stringResource(R.string.select_item))
        }
    ) {
        Column {
            HorizontalDivider()
            VaultItemType.entries.forEach {
                val interactionSource = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clickable(
                            onClick = { onSelect(it) },
                            interactionSource = interactionSource,
                            indication = ripple()
                        )
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = it.icon,
                            contentDescription = null
                        )
                        Text(text = it.getString())
                    }
                }
            }
            HorizontalDivider()
        }
    }
}

@Composable
internal fun AlertDialogContent(
    modifier: Modifier = Modifier,
    buttons: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    buttonContentColor: Color = MaterialTheme.colorScheme.primary,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = containerColor,
        tonalElevation = tonalElevation,
    ) {
        Column(modifier = Modifier.padding(DialogPadding)) {
            icon?.let {
                CompositionLocalProvider(LocalContentColor provides iconContentColor) {
                    Box(
                        Modifier
                            .padding(IconPadding)
                            .align(Alignment.CenterHorizontally)
                    ) {
                        icon()
                    }
                }
            }
            title?.let {
                ProvideContentColorTextStyle(
                    contentColor = titleContentColor,
                    textStyle = MaterialTheme.typography.headlineSmall
                ) {
                    Box(
                        // Align the title to the center when an icon is present.
                        Modifier
                            .padding(TitlePadding)
                            .align(
                                if (icon == null) {
                                    Alignment.Start
                                } else {
                                    Alignment.CenterHorizontally
                                }
                            )
                    ) {
                        title()
                    }
                }
            }
            val contentTextStyle = MaterialTheme.typography.bodyMedium
            ProvideContentColorTextStyle(
                contentColor = textContentColor,
                textStyle = contentTextStyle
            ) {
                Box(
                    Modifier
                        .weight(weight = 1f, fill = false)
                        .padding(TextPadding)
                        .align(Alignment.Start)
                ) {
                    content()
                }
            }
            buttons?.let {
                Box(modifier = Modifier.align(Alignment.End)) {
                    val textStyle = MaterialTheme.typography.labelLarge
                    ProvideContentColorTextStyle(
                        contentColor = buttonContentColor,
                        textStyle = textStyle,
                        content = buttons
                    )
                }
            }
        }
    }
}


private val DialogPadding = PaddingValues(all = 24.dp)
private val IconPadding = PaddingValues(bottom = 16.dp)
private val TitlePadding = PaddingValues(bottom = 16.dp)
private val TextPadding = PaddingValues(bottom = 24.dp)

@Composable
internal fun ProvideContentColorTextStyle(
    contentColor: Color,
    textStyle: TextStyle,
    content: @Composable () -> Unit
) {
    val mergedStyle = LocalTextStyle.current.merge(textStyle)
    CompositionLocalProvider(
        LocalContentColor provides contentColor,
        LocalTextStyle provides mergedStyle,
        content = content
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun SelectItemContentPreview() {
    val state = rememberModalBottomSheetState()
    LaunchedEffect(Unit) {
        state.expand()
    }
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            BasicAlertDialog(
                onDismissRequest = {},
            ) {
                SelectItemContent(onSelect = {})
            }
        }
    }
}