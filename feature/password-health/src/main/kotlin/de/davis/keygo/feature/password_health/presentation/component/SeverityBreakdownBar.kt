package de.davis.keygo.feature.password_health.presentation.component

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import de.davis.keygo.feature.password_health.R
import de.davis.keygo.feature.password_health.presentation.model.SeverityBreakdown
import kotlin.math.min

@Composable
internal fun SeverityBreakdownBar(
    breakdown: SeverityBreakdown,
    containerColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val ramp = remember(containerColor, accentColor) { severityRamp(containerColor, accentColor) }
    val critical by animateColorAsState(ramp.critical, label = "criticalColor")
    val high by animateColorAsState(ramp.high, label = "highColor")
    val medium by animateColorAsState(ramp.medium, label = "mediumColor")
    val segments = listOf(
        Segment(breakdown.critical, critical, R.string.breakdown_critical),
        Segment(breakdown.high, high, R.string.breakdown_high),
        Segment(breakdown.medium, medium, R.string.breakdown_medium),
        Segment(
            breakdown.clean,
            LocalContentColor.current.copy(alpha = TrackAlpha),
            R.string.breakdown_clean,
        ),
    )
    val weights = segments.map {
        animateFloatAsState(targetValue = it.count.toFloat(), label = "segmentWeight")
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clearAndSetSemantics {},
        ) {
            drawSegments(
                colors = segments.map { it.color },
                weights = weights.map { it.value },
                gap = 4.dp.toPx(),
            )
        }

        CountRow {
            segments.filter { it.count > 0 }.forEach {
                LegendEntry(it)
            }
        }
    }
}

private const val TrackAlpha = 0.14f

private data class Segment(
    val count: Int,
    val color: Color,
    @param:StringRes val label: Int,
)

// A segment's minimum width (a dot) and trailing gap scale with its weight up to 1, so a single
// finding among hundreds stays visible and segments grow in and out without jumping.
private fun DrawScope.drawSegments(colors: List<Color>, weights: List<Float>, gap: Float) {
    val total = weights.sum()
    if (total <= 0f) return

    val presence = weights.map { it.coerceIn(0f, 1f) }
    val presenceSum = presence.sum()
    val available = size.width - gap * (presenceSum - presence.last { it > 0f })
    val free = available - presenceSum * size.height

    var x = 0f
    weights.forEachIndexed { i, weight ->
        if (weight <= 0f) return@forEachIndexed

        val width =
            if (free >= 0f) presence[i] * size.height + free * weight / total
            else available * weight / total
        val left = if (layoutDirection == LayoutDirection.Rtl) size.width - x - width else x

        drawRoundRect(
            color = colors[i],
            topLeft = Offset(left, 0f),
            size = Size(width, size.height),
            cornerRadius = CornerRadius(min(width, size.height) / 2),
        )
        x += width + gap * presence[i]
    }
}

@Composable
private fun LegendEntry(segment: Segment) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(segment.color, CircleShape),
        )
        CountText(label = segment.label, count = segment.count)
    }
}

@Composable
private fun BreakdownPreviewCards() {
    Column {
        listOf(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.surfaceContainerHigh,
        ).forEach { container ->
            Surface(color = container) {
                SeverityBreakdownBar(
                    breakdown = SeverityBreakdown(critical = 1, high = 5, medium = 3, clean = 120),
                    containerColor = container,
                    accentColor =
                        if (container == MaterialTheme.colorScheme.surfaceContainerHigh) MaterialTheme.colorScheme.tertiary
                        else container,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Preview
@Composable
private fun SeverityBreakdownBarPreview() {
    MaterialTheme {
        BreakdownPreviewCards()
    }
}

@Preview
@Composable
private fun SeverityBreakdownBarDarkPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        BreakdownPreviewCards()
    }
}
