package de.davis.keygo.feature.password_health.presentation.component

import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import de.davis.keygo.core.ui.components.KeyGoSwitch
import de.davis.keygo.feature.password_health.R
import de.davis.keygo.feature.password_health.presentation.model.PasswordHealthUiState
import de.davis.keygo.feature.password_health.presentation.segmentContainerColor

@Composable
internal fun BreachCheck(
    state: PasswordHealthUiState,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    KeyGoSwitch(
        checked = state.breachCheckEnabled,
        onCheckedChange = onChange,
        modifier = modifier,
        supportingContent = {
            Text(text = stringResource(R.string.breach_check_opt_in_description))
        },
        verticalAlignment = Alignment.CenterVertically,
        colors = ListItemDefaults.segmentedColors(containerColor = segmentContainerColor),
        shapes = ListItemDefaults.shapes(shape = MaterialTheme.shapes.large)
    ) {
        Text(text = stringResource(R.string.breach_check_enable))
    }
}

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
