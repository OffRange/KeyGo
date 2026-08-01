package de.davis.keygo.feature.backup.presentation.component

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Reusable, step-based wizard chrome shared by the export and import backup flows.
 *
 * Owns the generic behaviour: an animated top progress indicator, a [TopAppBar] whose navigation
 * icon walks back through [steps] (or calls [navigateUp] on the first step), predictive-back that
 * seeks the step transition, and an optional bottom continue [Button]. Callers supply only the
 * per-step [title] and body [content] plus the label/icon rendered inside the continue button.
 *
 * @param currentStep must be an element of [steps].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> Wizard(
    steps: List<T>,
    currentStep: T,
    title: String,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    navigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    showContinueButton: Boolean = true,
    canContinue: Boolean = true,
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
                    Text(text = title)
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (currentIndex == 0) navigateUp()
                            else onBack()
                        }
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
            AnimatedVisibility(visible = showContinueButton) {
                Box(
                    modifier = Modifier
                        .padding(vertical = 12.dp, horizontal = 4.dp)
                        .imePadding(),
                ) {
                    Button(
                        onClick = onContinue,
                        enabled = canContinue,
                        modifier = Modifier.fillMaxWidth(),
                        content = continueButtonContent,
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .padding(start = 4.dp, end = 4.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(steps.size) { page ->
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
            transition.AnimatedContent(
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    fadeIn(animationSpec = tween(700)) togetherWith
                            fadeOut(animationSpec = tween(700))
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
