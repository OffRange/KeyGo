package de.davis.keygo.feature.password_health.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.ui.components.KeyGoSwitch
import de.davis.keygo.feature.password_health.R
import de.davis.keygo.feature.password_health.domain.model.CheckGap
import de.davis.keygo.feature.password_health.domain.model.CheckKind
import de.davis.keygo.feature.password_health.domain.model.GapReason
import de.davis.keygo.feature.password_health.presentation.model.PasswordHealthUiEvent
import de.davis.keygo.feature.password_health.presentation.model.PasswordHealthUiState
import de.davis.keygo.feature.password_health.presentation.segmentContainerColor

@Composable
internal fun HealthSettings(
    state: PasswordHealthUiState,
    onEvent: (PasswordHealthUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        BreachCheck(
            state = state,
            onChange = { onEvent(PasswordHealthUiEvent.OnBreachCheckChanged(it)) },
            shapes = ListItemDefaults.segmentedShapes(0, 2),
        )

        NotificationSwitch(
            checked = state.notificationEnabled,
            onChange = { onEvent(PasswordHealthUiEvent.OnNotificationChanged(it)) },
            shapes = ListItemDefaults.segmentedShapes(1, 2),
        )
    }
}

@Composable
private fun BreachCheck(
    state: PasswordHealthUiState,
    onChange: (Boolean) -> Unit,
    shapes: ListItemShapes,
) {
    val message = state.breachGap?.message()

    KeyGoSwitch(
        checked = state.breachCheckEnabled,
        onCheckedChange = onChange,
        supportingContent = {
            if (message == null)
                Text(text = stringResource(R.string.breach_check_opt_in_description))
            else Text(text = message, color = MaterialTheme.colorScheme.error)
        },
        verticalAlignment = Alignment.CenterVertically,
        colors = ListItemDefaults.segmentedColors(containerColor = segmentContainerColor),
        shapes = shapes,
    ) {
        Text(text = stringResource(R.string.breach_check_enable))
    }
}

@Composable
@ReadOnlyComposable
private fun CheckGap.message(): String? {
    val plural = when (reason) {
        // An off switch is the whole explanation, so there is nothing to warn about.
        GapReason.Disabled -> return null

        GapReason.Unreachable -> R.plurals.breach_check_unreachable
        GapReason.Failed -> R.plurals.breach_check_failed
    }

    return pluralStringResource(plural, unchecked.size, unchecked.size)
}

@Preview
@Composable
private fun BreachCheckPreview() {
    MaterialTheme {
        HealthSettings(
            state = PasswordHealthUiState(),
            onEvent = {},
        )
    }
}

@Preview
@Composable
private fun BreachCheckUnavailablePreview() {
    MaterialTheme {
        HealthSettings(
            state = PasswordHealthUiState(
                checkGaps = mapOf(
                    CheckKind.Breach to CheckGap(
                        reason = GapReason.Unreachable,
                        unchecked = setOf(newItemId(), newItemId(), newItemId()),
                    ),
                ),
            ),
            onEvent = {}
        )
    }
}
