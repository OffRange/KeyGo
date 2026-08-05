package de.davis.keygo.feature.onboarding.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import de.davis.keygo.feature.onboarding.R
import de.davis.keygo.feature.onboarding.presentation.component.LargeIconContainer
import de.davis.keygo.feature.onboarding.presentation.component.OnboardingScaffold
import de.davis.keygo.core.ui.R as CoreUiR

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun WelcomeContent(onContinue: () -> Unit) {
    OnboardingScaffold(
        iconContainer = {
            LargeIconContainer(
                shape = MaterialShapes.Arrow.toShape()
            ) {
                Icon(
                    painter = painterResource(CoreUiR.drawable.ic_launcher_monochrome),
                    contentDescription = null
                )
            }
        },
        title = stringResource(R.string.welcome_title),
        description = stringResource(R.string.welcome_subtitle),
        contentHorizontalAlignment = Alignment.CenterHorizontally
    ) {
        // No content for Welcome
    }
}

@Preview
@Composable
private fun WelcomeContentPreview() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            SharedTransitionLayout {
                AnimatedVisibility(visible = true) {
                    WelcomeContent(
                        onContinue = {}
                    )
                }
            }
        }
    }
}