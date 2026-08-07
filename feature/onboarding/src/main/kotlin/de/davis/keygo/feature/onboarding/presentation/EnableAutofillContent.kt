package de.davis.keygo.feature.onboarding.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.davis.keygo.feature.onboarding.R
import de.davis.keygo.feature.onboarding.presentation.component.OnboardingScaffold
import de.davis.keygo.feature.onboarding.presentation.component.SmallIconContainer
import de.davis.keygo.feature.onboarding.presentation.model.AutofillSetupStatus
import de.davis.keygo.feature.onboarding.presentation.model.AutofillSetupStep
import de.davis.keygo.feature.onboarding.presentation.model.OnboardingUiState
import de.davis.keygo.feature.onboarding.presentation.model.setupSteps

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun EnableAutofillContent(state: OnboardingUiState.EnableAutofill) {
    val steps = state.setupSteps()

    OnboardingScaffold(
        iconContainer = {
            SmallIconContainer(
                shape = MaterialShapes.Cookie7Sided.toShape()
            ) {
                Icon(
                    imageVector = Icons.Default.AutoFixHigh,
                    contentDescription = null
                )
            }
        },
        title = stringResource(R.string.autofill_title),
        description = stringResource(R.string.autofill_subtitle),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
        ) {
            steps.forEachIndexed { index, (step, status) ->
                SegmentedListItem(
                    shapes = ListItemDefaults.segmentedShapes(index, steps.size),
                    leadingContent = {
                        StepBadge(
                            number = index + 1,
                            status = status,
                        )
                    },
                    supportingContent = {
                        Text(text = stringResource(step.supportingRes))
                    }
                ) {
                    Text(text = stringResource(step.titleRes))
                }
            }
        }
    }
}

@Composable
internal fun StepBadge(
    number: Int,
    status: AutofillSetupStatus,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val containerColor = when (status) {
        AutofillSetupStatus.Current -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = when (status) {
        AutofillSetupStatus.Current -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        if (status == AutofillSetupStatus.Done)
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = contentColor,
            )
        else Text(
            text = number.toString(),
            color = contentColor,
            style = MaterialTheme.typography.headlineSmallEmphasized,
        )
    }
}

private val AutofillSetupStep.titleRes
    get() = when (this) {
        AutofillSetupStep.OpenSystemSettings -> R.string.autofill_open_settings
        AutofillSetupStep.ChooseKeyGo -> R.string.autofill_choose_keygo
        AutofillSetupStep.EnableInChrome -> R.string.autofill_chrome
    }

private val AutofillSetupStep.supportingRes
    get() = when (this) {
        AutofillSetupStep.OpenSystemSettings -> R.string.autofill_open_settings_support
        AutofillSetupStep.ChooseKeyGo -> R.string.autofill_choose_keygo_support
        AutofillSetupStep.EnableInChrome -> R.string.autofill_chrome_support
    }

@Preview
@Composable
private fun EnableAutofillContentPreview() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            EnableAutofillContent(
                state = OnboardingUiState.EnableAutofill(chromeAvailable = true)
            )
        }
    }
}

@Preview
@Composable
private fun EnableAutofillContentChromePendingPreview() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            EnableAutofillContent(
                state = OnboardingUiState.EnableAutofill(
                    systemAutofillEnabled = true,
                    chromeAvailable = true,
                )
            )
        }
    }
}
