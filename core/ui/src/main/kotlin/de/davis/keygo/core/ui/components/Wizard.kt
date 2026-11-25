package de.davis.keygo.core.ui.components

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

private val WizardHorizontalPadding = 24.dp

/**
 * Reusable, step-based wizard chrome shared by the backup and account flows.
 *
 * Owns the generic behaviour: an animated step indicator, a [TopAppBar] whose navigation icon walks
 * back through [steps] (or calls [navigateUp] on the first step), predictive-back that seeks the
 * step transition, and a full-width bottom continue [Button]. Callers supply the body [content]
 * plus the label/icon rendered inside the continue button.
 *
 * With a [title] the bar shows it and the step indicator sits below the bar; this suits steps
 * whose body is a list with no heading of its own. Without one the indicator moves into the bar,
 * the way onboarding does it, and the step's content carries its own headline.
 *
 * A step that offers a second, optional way forward (a "Scan invite") passes [secondaryAction]. It
 * sits above the continue button, so the main action stays full width and closest to the thumb.
 *
 * @param currentStep must be an element of [steps].
 */
@Composable
fun <T> Wizard(
    steps: List<T>,
    currentStep: T,
    title: String?,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    navigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    showContinueButton: Boolean = true,
    canContinue: Boolean = true,
    secondaryAction: (@Composable () -> Unit)? = null,
    continueButtonContent: @Composable RowScope.() -> Unit,
    content: @Composable (T) -> Unit,
) {
    val currentIndex = steps.indexOf(currentStep).coerceAtLeast(0)

    val transitionState = remember { SeekableTransitionState(currentStep) }
    val transition = rememberTransition(transitionState, label = "wizard_step")
    val resetScope = rememberCoroutineScope()

    LaunchedEffect(currentStep) {
        if (transitionState.currentState != currentStep)
            transitionState.animateTo(currentStep)
    }

    PredictiveBackHandler(enabled = currentIndex > 0) { progress ->
        val previousStep = steps[currentIndex - 1]
        try {
            progress.collect { backEvent ->
                transitionState.seekTo(backEvent.progress, targetState = previousStep)
            }
            onBack()
        } catch (_: CancellationException) {
            resetScope.launch { transitionState.animateTo(transitionState.currentState) }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    if (title != null) Text(text = title)
                    else StepIndicator(
                        stepCount = steps.size,
                        currentIndex = currentIndex,
                        modifier = Modifier.padding(end = WizardHorizontalPadding - 4.dp),
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (currentIndex == 0) navigateUp()
                            else onBack()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
        bottomBar = {
            // Outside a Row/Column the defaults are expandIn/shrinkOut, which grow from a corner.
            val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
            val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
            AnimatedVisibility(
                visible = showContinueButton,
                enter = fadeIn(effectsSpec) + expandVertically(
                    animationSpec = spatialSpec,
                    expandFrom = Alignment.Top,
                ),
                exit = fadeOut(effectsSpec) + shrinkVertically(
                    animationSpec = spatialSpec,
                    shrinkTowards = Alignment.Top,
                ),
            ) {
                val buttonHeight = ButtonDefaults.MediumContainerHeight
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = WizardHorizontalPadding, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    secondaryAction?.invoke()
                    Button(
                        onClick = onContinue,
                        enabled = canContinue,
                        shapes = ButtonDefaults.shapesFor(buttonHeight),
                        contentPadding = ButtonDefaults.contentPaddingFor(buttonHeight),
                        modifier = Modifier
                            .fillMaxWidth()
                            .sizeIn(minHeight = buttonHeight),
                    ) {
                        CompositionLocalProvider(
                            LocalTextStyle provides ButtonDefaults.textStyleFor(buttonHeight),
                        ) {
                            continueButtonContent()
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .padding(horizontal = WizardHorizontalPadding)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (title != null) StepIndicator(
                stepCount = steps.size,
                currentIndex = currentIndex,
            )

            transition.AnimatedContent(
                modifier = Modifier.weight(1f),
                // No SizeTransform: the slot's height is fixed by weight(1f), but it changes while
                // the bottom bar or IME animates. An animated size lags behind it, and Compose
                // centres the undersized content in the slot, so the step visibly jumps.
                transitionSpec = {
                    (fadeIn(animationSpec = tween(700)) togetherWith
                            fadeOut(animationSpec = tween(700))).using(null)
                },
            ) { step ->
                Surface(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    content(step)
                }
            }
        }
    }
}

@Composable
private fun StepIndicator(
    stepCount: Int,
    currentIndex: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(stepCount) { page ->
            val color by animateColorAsState(
                targetValue = if (page <= currentIndex) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.primaryContainer,
                label = "indicator_page",
            )
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .weight(1f)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}
