package de.davis.keygo.feature.password_health.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color


internal val segmentContainerColor: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceContainerHigh

internal val openedSegmentContainerColor: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primaryContainer
