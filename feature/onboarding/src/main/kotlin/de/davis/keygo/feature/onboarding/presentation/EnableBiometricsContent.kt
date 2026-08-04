package de.davis.keygo.feature.onboarding.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import de.davis.keygo.feature.onboarding.R
import de.davis.keygo.feature.onboarding.presentation.component.LargeIconContainer
import de.davis.keygo.feature.onboarding.presentation.component.OnboardingScaffold

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun EnableBiometricsContent(
    onContinue: () -> Unit
) {
    OnboardingScaffold(
        iconContainer = {
            LargeIconContainer(
                shape = MaterialShapes.Square.toShape()
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = null
                )
            }
        },
        title = stringResource(R.string.biometrics_title),
        description = stringResource(R.string.biometrics_subtitle),
        buttonText = stringResource(R.string.enable_biometrics),
        onButtonClicked = onContinue,
        optionalAction = {
            TextButton(
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.skip_for_now))
            }
        },
        contentHorizontalAlignment = Alignment.CenterHorizontally
    ) {
        // No content for Enable Biometrics
    }
}

@Preview
@Composable
private fun EnableBiometricsContentPreview() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            EnableBiometricsContent(
                onContinue = {}
            )
        }
    }
}