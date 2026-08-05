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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.davis.keygo.feature.onboarding.R
import de.davis.keygo.feature.onboarding.presentation.component.OnboardingScaffold
import de.davis.keygo.feature.onboarding.presentation.component.SmallIconContainer

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun EnableAutofillContent() {
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
            SegmentedListItem(
                shapes = ListItemDefaults.segmentedShapes(0, 3),
                leadingContent = {
                    NumberBadge(
                        number = 1,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                },
                supportingContent = {
                    Text(text = stringResource(R.string.autofill_open_settings_support))
                }
            ) {
                Text(text = stringResource(R.string.autofill_open_settings))
            }

            SegmentedListItem(
                shapes = ListItemDefaults.segmentedShapes(1, 3),
                leadingContent = {
                    NumberBadge(
                        number = 2,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                },
                supportingContent = {
                    Text(text = stringResource(R.string.autofill_choose_keygo_support))
                }
            ) {
                Text(text = stringResource(R.string.autofill_choose_keygo))
            }

            SegmentedListItem(
                shapes = ListItemDefaults.segmentedShapes(2, 3),
                leadingContent = {
                    NumberBadge(
                        number = 3,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                },
                supportingContent = {
                    Text(text = stringResource(R.string.autofill_chrome_support))
                }
            ) {
                Text(text = stringResource(R.string.autofill_chrome))
            }
        }
    }
}

@Composable
internal fun NumberBadge(
    number: Int,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = number.toString(),
            color = contentColor,
            style = MaterialTheme.typography.headlineSmallEmphasized,
        )
    }
}


@Preview
@Composable
private fun EnableAutofillContentPreview() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            EnableAutofillContent()
        }
    }
}