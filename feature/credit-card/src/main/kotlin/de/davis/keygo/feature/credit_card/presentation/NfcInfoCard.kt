package de.davis.keygo.feature.credit_card.presentation

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.davis.keygo.core.security.presentation.rememberHandoffStarter
import de.davis.keygo.feature.credit_card.R
import de.davis.keygo.feature.credit_card.domain.model.Card
import de.davis.keygo.feature.credit_card.domain.model.CardReadFailure
import java.time.YearMonth

private const val DescriptionLines = 2
private val IndicatorSize = 56.dp

// Base gap under the indicator, the gap between title and description, and the
// gap above the action. The indicator gap and the action area share a single
// animated driver: as the action reveals, the gap above the text shrinks by the
// same amount, so the text slides up while the total height stays constant.
private val IndicatorGap = 16.dp
private val TitleGap = 8.dp
private val ActionGap = 16.dp

private data class ScanContent(
    val indicator: Indicator,
    val titleRes: Int,
    val descriptionRes: Int? = null,
    val action: Action? = null,
)

private sealed interface Indicator {
    data class Static(val icon: ImageVector, val isError: Boolean = false) : Indicator
    data object Loading : Indicator
}

private data class Action(val labelRes: Int, val icon: ImageVector, val onClick: () -> Unit)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun NfcInfoCard(
    state: CardScanUiState,
    nfcEnabled: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val openSystemScreen = rememberHandoffStarter()
    val onEnableNfc = { openSystemScreen.launch(Intent(Settings.ACTION_NFC_SETTINGS)) }

    // 0 = no action (text sits lower under the indicator), 1 = action shown
    // (text slid up, action revealed below). Hoisted out of AnimatedContent so a
    // single value drives the slide across the content crossfade.
    val actionProgress by animateFloatAsState(
        targetValue = if (state.hasAction(nfcEnabled)) 1f else 0f,
        label = "action-progress",
    )

    AnimatedContent(
        targetState = state to nfcEnabled,
        modifier = modifier.fillMaxWidth(),
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        contentAlignment = Alignment.TopCenter,
        label = "card-scan-content",
    ) { (state, nfcEnabled) ->
        val content = state.toContent(nfcEnabled, onRetry, onEnableNfc)
        val actionArea = ActionGap + ButtonDefaults.MinHeight

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ScanIndicator(content.indicator)

            // The unused action area is folded into the gap above the text, so
            // the text rests lower until an action claims that space back.
            // actionProgress is read in the layout phase (see animatedHeight) so
            // the slide re-measures without recomposing this content each frame.
            Spacer(Modifier.animatedHeight { IndicatorGap + actionArea * (1f - actionProgress) })

            Text(
                text = stringResource(content.titleRes),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(TitleGap))

            // Always reserve the description slot so states without one keep the
            // same height as states with one.
            Text(
                text = content.descriptionRes?.let { stringResource(it) }.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                minLines = DescriptionLines,
            )

            // Grows from zero to the action's full height as the action reveals;
            // the button is anchored to the bottom and clipped while it slides in.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    .animatedHeight { actionArea * actionProgress },
                contentAlignment = Alignment.BottomCenter,
            ) {
                content.action?.let { action ->
                    OutlinedButton(onClick = action.onClick) {
                        Icon(
                            imageVector = action.icon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = stringResource(action.labelRes),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Fixes the element's height to [height], reading it in the layout phase. Used
 * for animated heights so an animating value re-measures without recomposing the
 * wrapped content each frame. Mirrors `Modifier.height` otherwise.
 */
private fun Modifier.animatedHeight(height: () -> Dp) = layout { measurable, constraints ->
    val h = height().roundToPx().coerceAtLeast(0)
    val placeable = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
    layout(placeable.width, h) { placeable.place(0, 0) }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ScanIndicator(indicator: Indicator) {
    Box(
        modifier = Modifier.size(IndicatorSize),
        contentAlignment = Alignment.Center,
    ) {
        when (indicator) {
            is Indicator.Static -> Icon(
                imageVector = indicator.icon,
                contentDescription = null,
                modifier = Modifier.size(IndicatorSize),
                tint = if (indicator.isError) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
            )

            Indicator.Loading -> CircularWavyProgressIndicator(
                modifier = Modifier.size(IndicatorSize),
            )
        }
    }
}

// Mirrors which states in [toContent] carry an action, without building the
// content. Drives the reveal animation from the latest target state.
private fun CardScanUiState.hasAction(nfcEnabled: Boolean): Boolean = when (this) {
    CardScanUiState.Ready -> !nfcEnabled
    is CardScanUiState.Failure -> true
    else -> false
}

private fun CardScanUiState.toContent(
    nfcEnabled: Boolean,
    onRetry: () -> Unit,
    onEnableNfc: () -> Unit,
): ScanContent = when (this) {
    CardScanUiState.Ready -> if (nfcEnabled) ScanContent(
        indicator = Indicator.Static(Icons.Default.Contactless),
        titleRes = R.string.card_scan_ready_title,
        descriptionRes = R.string.card_scan_ready_message,
    ) else ScanContent(
        indicator = Indicator.Static(Icons.Default.ErrorOutline, isError = true),
        titleRes = R.string.card_scan_nfc_disabled_title,
        descriptionRes = R.string.card_scan_nfc_disabled_message,
        action = Action(R.string.card_scan_enable_nfc, Icons.Default.Settings, onEnableNfc),
    )

    CardScanUiState.Reading -> ScanContent(
        indicator = Indicator.Loading,
        titleRes = R.string.card_scan_reading,
        descriptionRes = R.string.card_scan_reading_message,
    )

    is CardScanUiState.Success -> ScanContent(
        indicator = Indicator.Static(Icons.Default.CheckCircle),
        titleRes = R.string.card_scan_success,
    )

    is CardScanUiState.Failure -> ScanContent(
        indicator = Indicator.Static(Icons.Default.ErrorOutline, isError = true),
        titleRes = R.string.card_scan_error_title,
        descriptionRes = reason.messageRes(),
        action = Action(R.string.card_scan_retry, Icons.Default.Refresh, onRetry),
    )
}

private class UserStateProvider : PreviewParameterProvider<UserStateProvider.State> {

    data class State(val cardState: CardScanUiState, val nfcEnabled: Boolean = true)

    override val values = sequenceOf(
        State(cardState = CardScanUiState.Ready, nfcEnabled = false),
        State(cardState = CardScanUiState.Ready),
        State(cardState = CardScanUiState.Reading),
        State(cardState = CardScanUiState.Failure(CardReadFailure.NoReadableData)),
        State(
            cardState = CardScanUiState.Success(
                Card(
                    holder = "",
                    cardNumber = "",
                    expiry = YearMonth.now()
                )
            )
        ),
    )
}

@Preview
@Composable
private fun NfcInfoCardPreview(
    @PreviewParameter(UserStateProvider::class)
    state: UserStateProvider.State
) {
    MaterialTheme {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            NfcInfoCard(
                state = state.cardState,
                nfcEnabled = state.nfcEnabled,
                onRetry = {},
            )
        }
    }
}

private fun CardReadFailure.messageRes(): Int = when (this) {
    CardReadFailure.NotAnEmvCard -> R.string.card_scan_error_not_emv
    CardReadFailure.TagLost -> R.string.card_scan_error_tag_lost
    CardReadFailure.NoReadableData -> R.string.card_scan_error_no_data
    is CardReadFailure.Unexpected -> R.string.card_scan_error_unexpected
}
