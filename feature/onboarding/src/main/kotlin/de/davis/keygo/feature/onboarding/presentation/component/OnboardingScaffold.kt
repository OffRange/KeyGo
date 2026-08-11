package de.davis.keygo.feature.onboarding.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
internal fun SmallIconContainer(
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .size(100.dp)
            .aspectRatio(1f),
        shape = shape,
        color = MaterialTheme.colorScheme.primary,
    ) {
        Box(
            modifier = Modifier.requiredSize(48.dp),
            contentAlignment = Alignment.Center,
            propagateMinConstraints = true,
            content = content,
        )
    }
}

@Composable
internal fun LargeIconContainer(
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .size(250.dp)
            .aspectRatio(1f),
        shape = shape,
        color = MaterialTheme.colorScheme.primary,
    ) {
        Box(
            modifier = Modifier.requiredSize(175.dp),
            contentAlignment = Alignment.Center,
            propagateMinConstraints = true,
            content = content,
        )
    }
}

private fun arrangementFor(horizontal: Alignment.Horizontal) = when (horizontal) {
    Alignment.CenterHorizontally -> Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
    else -> Arrangement.spacedBy(8.dp)
}

private fun textAlignFor(horizontal: Alignment.Horizontal) = when (horizontal) {
    Alignment.CenterHorizontally -> TextAlign.Center
    else -> null
}

@Composable
internal fun OnboardingScaffold(
    iconContainer: @Composable () -> Unit,
    title: String,
    description: String,
    contentHorizontalAlignment: Alignment.Horizontal = Alignment.Start,
    info: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = arrangementFor(contentHorizontalAlignment),
            horizontalAlignment = contentHorizontalAlignment,
        ) {
            iconContainer()

            Text(
                text = title,
                style = MaterialTheme.typography.headlineLargeEmphasized,
                modifier = Modifier.fillMaxWidth(),
                textAlign = textAlignFor(contentHorizontalAlignment),
            )

            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth(if (contentHorizontalAlignment == Alignment.CenterHorizontally) 0.75f else 1f),
                textAlign = textAlignFor(contentHorizontalAlignment),
            )

            content()
        }

        info?.invoke()
    }
}