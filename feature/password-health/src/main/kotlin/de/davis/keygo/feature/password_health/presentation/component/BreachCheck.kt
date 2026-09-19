package de.davis.keygo.feature.password_health.presentation.component

import androidx.compose.material3.ListItemDefaults
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
import de.davis.keygo.feature.password_health.domain.model.CheckError
import de.davis.keygo.feature.password_health.domain.model.CheckGap
import de.davis.keygo.feature.password_health.domain.model.CheckKind
import de.davis.keygo.feature.password_health.presentation.model.PasswordHealthUiState
import de.davis.keygo.feature.password_health.presentation.segmentContainerColor

@Composable
internal fun BreachCheck(
    state: PasswordHealthUiState,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gap = state.breachGap

    KeyGoSwitch(
        checked = state.breachCheckEnabled,
        onCheckedChange = onChange,
        modifier = modifier,
        supportingContent = {
            if (gap == null) Text(text = stringResource(R.string.breach_check_opt_in_description))
            else Text(text = gap.message(), color = MaterialTheme.colorScheme.error)
        },
        verticalAlignment = Alignment.CenterVertically,
        colors = ListItemDefaults.segmentedColors(containerColor = segmentContainerColor),
        shapes = ListItemDefaults.shapes(shape = MaterialTheme.shapes.large)
    ) {
        Text(text = stringResource(R.string.breach_check_enable))
    }
}

@Composable
@ReadOnlyComposable
private fun CheckGap.message(): String = pluralStringResource(
    when (error) {
        CheckError.Unreachable -> R.plurals.breach_check_unreachable
        CheckError.Failed -> R.plurals.breach_check_failed
    },
    unchecked.size,
    unchecked.size,
)

@Preview
@Composable
private fun BreachCheckPreview() {
    MaterialTheme {
        BreachCheck(
            state = PasswordHealthUiState(),
            onChange = {}
        )
    }
}

@Preview
@Composable
private fun BreachCheckUnavailablePreview() {
    MaterialTheme {
        BreachCheck(
            state = PasswordHealthUiState(
                checkGaps = mapOf(
                    CheckKind.Breach to CheckGap(
                        error = CheckError.Unreachable,
                        unchecked = setOf(newItemId(), newItemId(), newItemId()),
                    ),
                ),
            ),
            onChange = {}
        )
    }
}
