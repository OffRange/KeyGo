package de.davis.keygo.feature.backup.presentation.export

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedToggleButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.davis.keygo.core.ui.components.KeyGoSwitch
import de.davis.keygo.feature.backup.R
import de.davis.keygo.feature.backup.domain.model.BackupInterval
import de.davis.keygo.feature.backup.domain.model.IntervalUnit
import de.davis.keygo.feature.backup.presentation.displayName
import de.davis.keygo.feature.backup.presentation.export.model.ExportWizardUiEvent
import de.davis.keygo.feature.backup.presentation.export.model.ScheduleMode
import de.davis.keygo.feature.backup.presentation.label
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun IntervalPicker(
    interval: BackupInterval,
    onEvent: (ExportWizardUiEvent) -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    val count = interval.count
    val unit = interval.unit
    ScheduleCard(
        title = stringResource(R.string.schedule_repeat_every_label),
        footerText = interval.displayName,
        footerIcon = Icons.Default.Schedule,
        shape = shape,
        modifier = modifier,
    ) {
        NumberStepper(
            value = count,
            onValueChange = { onEvent(ExportWizardUiEvent.IntervalCountChanged(it)) },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            IntervalUnit.entries.forEachIndexed { index, entry ->
                OutlinedToggleButton(
                    checked = unit == entry,
                    onCheckedChange = { onEvent(ExportWizardUiEvent.IntervalUnitSelected(entry)) },
                    modifier = Modifier
                        .weight(1f)
                        .semantics { role = Role.RadioButton },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        else -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    },
                    colors = ToggleButtonDefaults.outlinedToggleButtonColors(
                        checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                ) {
                    Text(text = entry.label)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RetentionPicker(
    keepCount: Int,
    keepAll: Boolean,
    onEvent: (ExportWizardUiEvent) -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    val expanded = !keepAll
    ScheduleCard(
        title = stringResource(R.string.schedule_auto_delete_label),
        footerText = if (keepAll) stringResource(R.string.schedule_keep_all_summary)
        else pluralStringResource(
            R.plurals.schedule_keep_summary,
            keepCount,
            keepCount,
        ),
        footerIcon = if (keepAll) Icons.Default.AllInclusive else Icons.Default.DeleteSweep,
        shape = shape,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            KeyGoSwitch(
                checked = expanded,
                onCheckedChange = { onEvent(ExportWizardUiEvent.KeepAllChanged(!it)) },
                colors = ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                Text(text = stringResource(R.string.enable))
            }

            // The top gap lives inside the animated region so it shrinks with the stepper.
            // Keeping AnimatedVisibility out of the parent's spacedBy avoids the snap that
            // happens when its layout node is dropped at the end of the exit transition.
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                ) {
                    NumberStepper(
                        value = keepCount,
                        onValueChange = { onEvent(ExportWizardUiEvent.KeepCountChanged(it)) },
                        supporting = {
                            Text(
                                text = pluralStringResource(
                                    R.plurals.schedule_keep_unit,
                                    keepCount
                                ),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduleCard(
    title: String,
    footerText: String,
    footerIcon: ImageVector,
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = shape,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )

            content()

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant,
                    LocalTextStyle provides LocalTextStyle.current.merge(MaterialTheme.typography.bodyMedium)
                ) {
                    Icon(
                        imageVector = footerIcon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )

                    Text(text = footerText)
                }
            }
        }
    }
}

@Composable
private fun NumberStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    supporting: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperButton(
            onStep = { onValueChange((value - 1).coerceAtLeast(STEPPER_MIN)) },
            imageVector = Icons.Default.Remove,
            enabled = value > STEPPER_MIN,
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            StepperValueField(
                value = value,
                onValueChange = onValueChange,
            )

            CompositionLocalProvider(
                LocalTextStyle provides MaterialTheme.typography.bodySmall,
                LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                supporting?.invoke()
            }
        }

        StepperButton(
            onStep = { onValueChange(value + 1) },
            imageVector = Icons.Default.Add,
            enabled = true,
        )
    }
}

@Composable
private fun StepperButton(
    onStep: () -> Unit,
    imageVector: ImageVector,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val currentStep by rememberUpdatedState(onStep)
    LaunchedEffect(pressed, enabled) {
        if (!pressed || !enabled) return@LaunchedEffect
        delay(STEP_INITIAL_DELAY_MS)
        var interval = STEP_REPEAT_START_MS
        while (true) {
            currentStep()
            delay(interval)
            interval = (interval * STEP_REPEAT_DECAY).coerceAtLeast(STEP_REPEAT_MIN_MS)
        }
    }
    FilledTonalIconButton(
        onClick = onStep,
        modifier = modifier,
        enabled = enabled,
        interactionSource = interactionSource,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepperValueField(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    var text by remember { mutableStateOf(value.toString()) }
    var focused by remember { mutableStateOf(false) }
    // Re-sync from the source of truth only while the user is not actively editing.
    LaunchedEffect(value) {
        if (!focused) text = value.toString()
    }

    val commit = {
        val committed = text.toIntOrNull()?.coerceAtLeast(STEPPER_MIN)
        if (committed != null) onValueChange(committed)
        text = (committed ?: value).toString()
    }

    // Drop focus the moment the keyboard is dismissed (Done or system back) so the cursor
    // disappears; onFocusChanged then commits, falling back to the known value if empty.
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible) {
        if (focused && !imeVisible) focusManager.clearFocus()
    }

    BasicTextField(
        value = text,
        onValueChange = { new -> if (new.all(Char::isDigit)) text = new },
        modifier = modifier
            .width(72.dp)
            .onFocusChanged { focusState ->
                if (focused && !focusState.isFocused) commit()
                focused = focusState.isFocused
            },
        textStyle = MaterialTheme.typography.headlineMedium.copy(
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        singleLine = true,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
    )
}

private const val STEPPER_MIN = 1

private val STEP_INITIAL_DELAY_MS = 350L.milliseconds
private val STEP_REPEAT_START_MS = 280L.milliseconds
private val STEP_REPEAT_MIN_MS = 45L.milliseconds
private const val STEP_REPEAT_DECAY = 0.82

internal val ScheduleMode.icon: ImageVector
    get() = when (this) {
        ScheduleMode.OneTime -> Icons.Default.Bolt
        ScheduleMode.Recurring -> Icons.Default.Autorenew
    }

internal val ScheduleMode.titleRes: Int
    get() = when (this) {
        ScheduleMode.OneTime -> R.string.schedule_one_time_title
        ScheduleMode.Recurring -> R.string.schedule_recurring_title
    }

internal val ScheduleMode.descriptionRes: Int
    get() = when (this) {
        ScheduleMode.OneTime -> R.string.schedule_one_time_description
        ScheduleMode.Recurring -> R.string.schedule_recurring_description
    }

internal val SegmentTopShape = RoundedCornerShape(
    topStart = 16.dp,
    topEnd = 16.dp,
    bottomStart = 4.dp,
    bottomEnd = 4.dp,
)

internal val SegmentBottomShape = RoundedCornerShape(
    topStart = 4.dp,
    topEnd = 4.dp,
    bottomStart = 16.dp,
    bottomEnd = 16.dp,
)
