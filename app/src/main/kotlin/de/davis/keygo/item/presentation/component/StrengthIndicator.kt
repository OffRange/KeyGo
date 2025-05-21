package de.davis.keygo.item.presentation.component

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.davis.keygo.R
import de.davis.keygo.item.domain.model.Score

@SuppressLint("UnusedTransitionTargetStateParameter")
@Composable
fun StrengthIndicator(
    score: Score,
    forceCompact: Boolean = false,
) {
    val targetTrackColor =
        if (score.isNone || score == Score.Excellent)
            MaterialTheme.colorScheme.secondaryContainer
        else
            MaterialTheme.colorScheme.errorContainer

    val targetIndicatorColor =
        if (score.isNone || score == Score.Excellent)
            MaterialTheme.colorScheme.onSecondaryContainer
        else
            MaterialTheme.colorScheme.error

    Row(
        verticalAlignment = Alignment.Companion.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            repeat(5) {
                val backgroundColor by animateColorAsState(
                    if (it < score.ordinal) targetIndicatorColor else targetTrackColor,
                    label = "Indicator color $it"
                )
                Box(
                    modifier = Modifier.Companion
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(backgroundColor)
                )
            }
        }

        val contentColor by animateColorAsState(
            targetIndicatorColor,
            label = "Content color"
        )
        
        CompositionLocalProvider(
            LocalContentColor provides contentColor,
            LocalTextStyle provides MaterialTheme.typography.bodySmall,
        ) {
            AnimatedVisibility(
                visible = !score.isNone && !forceCompact,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                val text = when (score) {
                    Score.None,
                    Score.Ridiculous -> stringResource(R.string.password_strength_ridiculous)

                    Score.Weak -> stringResource(R.string.password_strength_weak)
                    Score.Moderate -> stringResource(R.string.password_strength_moderate)
                    Score.Strong -> stringResource(R.string.password_strength_strong)
                    Score.Excellent -> stringResource(R.string.password_strength_excellent)
                }

                Text(text = text)
            }
        }
    }
}