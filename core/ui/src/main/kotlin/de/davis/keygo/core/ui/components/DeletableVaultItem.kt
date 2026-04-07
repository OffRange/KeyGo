@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package de.davis.keygo.core.ui.components

import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.davis.keygo.core.ui.R
import de.davis.keygo.core.ui.theme.KeyGoTheme
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private fun CornerBasedShape.lerpShape(other: CornerBasedShape, fraction: Float): CornerBasedShape =
    lerp(other as Any, fraction) as CornerBasedShape

private val CircleRoundedShape = RoundedCornerShape(50)

@Stable
class SegmentedShapeCoordinator(
    private val overrideShape: RoundedCornerShape,
    private val defaultShapes: ListItemShapes,
    private val defaultBaseShape: CornerBasedShape,
) {
    var swipingIndex by mutableStateOf<Int?>(null)
        internal set
    var swipingProgress by mutableFloatStateOf(0f)
        internal set

    fun onSwipeProgressChange(index: Int, progress: Float) {
        if (progress > 0f) {
            swipingIndex = index
            swipingProgress = progress
        } else if (swipingIndex == index) {
            swipingIndex = null
            swipingProgress = 0f
        }
    }

    internal fun computeShapes(
        isFirstFraction: Float,
        isLastFraction: Float,
        isSwiping: Boolean,
        morphIsFirstProgress: Float,
        morphIsLastProgress: Float,
    ): ListItemShapes {
        // Position-animated base: lerp top/bottom corners independently
        val topTarget = defaultBaseShape.copy(
            topStart = overrideShape.topStart,
            topEnd = overrideShape.topEnd,
        )
        val topLerped = defaultBaseShape.lerpShape(topTarget, isFirstFraction)

        val bottomTarget = defaultBaseShape.copy(
            bottomStart = overrideShape.bottomStart,
            bottomEnd = overrideShape.bottomEnd,
        )
        val bottomLerped = defaultBaseShape.lerpShape(bottomTarget, isLastFraction)

        val baseShape = topLerped.copy(
            bottomStart = bottomLerped.bottomStart,
            bottomEnd = bottomLerped.bottomEnd,
        )

        if (morphIsFirstProgress == 0f && morphIsLastProgress == 0f)
            return defaultShapes.copy(shape = baseShape)

        // Swipe morphing on top of the position-animated base
        val morphTarget = if (isSwiping) CircleRoundedShape else overrideShape

        val swipeTopTarget = baseShape.copy(
            topStart = morphTarget.topStart,
            topEnd = morphTarget.topEnd,
        )
        val swipeTopLerped = baseShape.lerpShape(swipeTopTarget, morphIsFirstProgress)

        val swipeBottomTarget = baseShape.copy(
            bottomStart = morphTarget.bottomStart,
            bottomEnd = morphTarget.bottomEnd,
        )
        val swipeBottomLerped = baseShape.lerpShape(swipeBottomTarget, morphIsLastProgress)

        return defaultShapes.copy(
            shape = swipeTopLerped.copy(
                bottomStart = swipeBottomLerped.bottomStart,
                bottomEnd = swipeBottomLerped.bottomEnd,
            )
        )
    }
}

@Composable
fun rememberSegmentedShapeCoordinator(
    defaultShapes: ListItemShapes = ListItemDefaults.shapes(),
): SegmentedShapeCoordinator {
    val overrideShape = MaterialTheme.shapes.large as RoundedCornerShape
    val defaultBaseShape = defaultShapes.shape
    return remember(overrideShape, defaultShapes) {
        SegmentedShapeCoordinator(
            overrideShape = overrideShape,
            defaultShapes = defaultShapes,
            defaultBaseShape = defaultBaseShape as CornerBasedShape,
        )
    }
}

@Composable
fun rememberSegmentedShapes(
    coordinator: SegmentedShapeCoordinator,
    index: Int,
    isFirst: Boolean,
    isLast: Boolean,
): ListItemShapes {
    val isFirstFraction by animateFloatAsState(if (isFirst) 1f else 0f)
    val isLastFraction by animateFloatAsState(if (isLast) 1f else 0f)

    val swiping = coordinator.swipingIndex
    val progress = if (swiping == null) 0f else coordinator.swipingProgress

    val targetFirst = when (swiping) {
        index -> progress
        index - 1 if !isFirst -> progress
        else -> 0f
    }
    val targetLast = when (swiping) {
        index -> progress
        index + 1 if !isLast -> progress
        else -> 0f
    }

    val morphFirst by animateFloatAsState(targetFirst)
    val morphLast by animateFloatAsState(targetLast)
    val isSwiping = swiping == index

    return remember(isFirstFraction, isLastFraction, isSwiping, morphFirst, morphLast) {
        coordinator.computeShapes(isFirstFraction, isLastFraction, isSwiping, morphFirst, morphLast)
    }
}

@Composable
fun DeletableVaultItem(
    title: String,
    description: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDeleteRequested: suspend () -> Boolean,
    shapeCoordinator: SegmentedShapeCoordinator,
    itemIndex: Int,
    isFirst: Boolean,
    isLast: Boolean,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    selectedContainerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    enableSwipeToDelete: Boolean = true,
    shapeMorphThreshold: Dp = 48.dp,
    leadingContent: @Composable (() -> Unit)? = null,
) {
    val shapes = rememberSegmentedShapes(shapeCoordinator, itemIndex, isFirst, isLast)
    val state = rememberSwipeToDismissBoxState()

    val currentOnDeleteRequested by rememberUpdatedState(onDeleteRequested)
    val scope = rememberCoroutineScope()

    val morphThresholdPx = with(LocalDensity.current) { shapeMorphThreshold.toPx() }
    LaunchedEffect(state, morphThresholdPx) {
        snapshotFlow {
            (state.offsetOrZero().absoluteValue / morphThresholdPx).coerceIn(0f, 1f)
        }.collect { shapeCoordinator.onSwipeProgressChange(itemIndex, it) }
    }
    DisposableEffect(Unit) {
        onDispose { shapeCoordinator.onSwipeProgressChange(itemIndex, 0f) }
    }

    HapticSwipeToDismissBox(
        backgroundContent = {
            DeletableVaultItemBackground(state = state)
        },
        modifier = modifier,
        allowSwipe = enableSwipeToDelete,
        state = state,
        onDismiss = {
            scope.launch {
                val shouldDelete = currentOnDeleteRequested()
                if (!shouldDelete) {
                    state.snapTo(SwipeToDismissBoxValue.Settled)
                }
            }
        }
    ) {
        SegmentedListItem(
            selected = selected,
            onClick = onClick,
            onLongClick = onLongClick,
            shapes = shapes,
            colors = ListItemDefaults.segmentedColors(
                containerColor = containerColor,
                selectedContainerColor = selectedContainerColor,
            ),
            leadingContent = leadingContent,
            supportingContent = {
                Text(text = description)
            }
        ) {
            Text(text = title)
        }
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
    val shapeCoordinator = rememberSegmentedShapeCoordinator()
    KeyGoTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column {
                DeletableVaultItem(
                    title = "My Element",
                    description = "Password",
                    onClick = {},
                    onLongClick = {},
                    onDeleteRequested = {
                        false
                    },
                    shapeCoordinator = shapeCoordinator,
                    itemIndex = 3,
                    isFirst = false,
                    isLast = true,
                )
            }
        }
    }
}