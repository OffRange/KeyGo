package de.davis.keygo.core.ui.components

import android.content.res.Configuration
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.davis.keygo.core.ui.R
import de.davis.keygo.core.ui.theme.KeyGoTheme
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@Composable
fun DeletableVaultItem(
    title: String,
    description: String,
    onDeleteRequested: suspend () -> Boolean,
    modifier: Modifier = Modifier,
    enableSwipeToDelete: Boolean = true,
    cardColors: CardColors = CardDefaults.cardColors(),
    leadingContent: @Composable (() -> Unit)? = null,
) {
    val state = rememberSwipeToDismissBoxState()

    val currentOnDeleteRequested by rememberUpdatedState(onDeleteRequested)
    val scope = rememberCoroutineScope()

    HapticSwipeToDismissBox(
        backgroundContent = {
            DeletableVaultItemBackground(state = state)
        },
        modifier = Modifier
            .clip(CardDefaults.shape)
            .then(modifier),
        allowSwipe = enableSwipeToDelete,
        state = state,
        onDismiss = {
            scope.launch {
                val shouldDelete = currentOnDeleteRequested()
                if (!shouldDelete) {
                    state.reset()
                }
            }
        }
    ) {
        VaultItem(
            headlineContent = {
                Text(text = title)
            },
            supportingContent = {
                Text(text = description)
            },
            leadingContent = leadingContent,
            cardColors = cardColors
        )
    }
}

@Composable
fun DeletableVaultItemBackground(state: SwipeToDismissBoxState) {
    val containerColor = MaterialTheme.colorScheme.error
    val contentColor = MaterialTheme.colorScheme.onError
    val gap = 4.dp
    val iconSize = 28.dp

    var itemHeightPx by remember { mutableFloatStateOf(0f) }
    val gapPx = with(LocalDensity.current) { gap.toPx() }

    val isIconFullyVisible by remember {
        derivedStateOf {
            if (itemHeightPx == 0f) false else {
                val offset = state.offsetOrZero()
                val drawWidth = offset.absoluteValue - gapPx

                // Alpha hits 1f exactly when the drawWidth equals the item height (forming a perfect circle)
                drawWidth >= itemHeightPx
            }
        }
    }

    val animatedTrashCan = AnimatedImageVector.animatedVectorResource(R.drawable.avd_delete_forever)
    val painter = rememberAnimatedVectorPainter(
        animatedImageVector = animatedTrashCan,
        atEnd = isIconFullyVisible
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { itemHeightPx = it.height.toFloat() }
            .drawBehind {
                val offset = state.offsetOrZero()

                val swipedWidth = offset.absoluteValue
                val drawWidth = swipedWidth - gapPx
                if (drawWidth < 1f) return@drawBehind

                val left = if (offset > 0f) 0f else size.width - drawWidth

                drawRoundRect(
                    color = containerColor,
                    topLeft = Offset(left, 0f),
                    size = Size(drawWidth, size.height),
                    cornerRadius = CornerRadius(size.height / 2f),
                )

                val iconSizePx = iconSize.toPx()

                val targetX = left + (drawWidth / 2f) - (iconSizePx / 2f)
                val targetY = (size.height / 2f) - (iconSizePx / 2f)

                // linear normalization
                val iconAlpha = drawWidth.mapToFraction(iconSizePx + gapPx, size.height)

                translate(left = targetX, top = targetY) {
                    with(painter) {
                        draw(
                            size = Size(iconSizePx, iconSizePx),
                            alpha = iconAlpha,
                            colorFilter = ColorFilter.tint(contentColor)
                        )
                    }
                }
            },
    )
}

/**
 * Maps a continuous Float value to a 0f..1f fraction based on a given range.
 * The result is safely clamped between 0f and 1f.
 *
 * @receiver The value to be mapped.
 * @param min The minimum value of the range.
 * @param max The maximum value of the range.
 *
 * @return The corresponding fraction between 0f and 1f.
 */
private fun Float.mapToFraction(min: Float, max: Float): Float {
    val range = (max - min).coerceAtLeast(1f) // Prevent division by zero
    return ((this - min) / range).coerceIn(0f, 1f)
}

private fun SwipeToDismissBoxState.offsetOrZero(): Float = runCatching {
    requireOffset()
}.getOrDefault(0f)

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Composable
private fun VaultItemPreview() {
    KeyGoTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column {
                DeletableVaultItem(
                    title = "My Element",
                    description = "Password",
                    onDeleteRequested = {
                        false
                    }
                )
            }
        }
    }
}