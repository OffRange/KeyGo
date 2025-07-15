package de.davis.keygo.dashboard.presentation.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun DeletableVaultItem(
    title: String,
    description: String,
    onDeleteRequested: () -> Boolean,
    modifier: Modifier = Modifier,
    cardColors: CardColors = CardDefaults.cardColors(),
    leadingContent: @Composable (() -> Unit)? = null,
) {
    val state = rememberSwipeToDismissBoxState(
        positionalThreshold = {
            it * .25f
        }
    )

    val currentOnDeleteRequested by rememberUpdatedState(onDeleteRequested)

    var prev by remember { mutableStateOf(state.currentValue) }

    LaunchedEffect(Unit) {
        state.reset()
    }

    LaunchedEffect(state.currentValue) {
        if (prev != state.currentValue &&
            state.currentValue == SwipeToDismissBoxValue.EndToStart
        ) {
            val deleted = currentOnDeleteRequested()
            if (!deleted) state.reset()
        }
        prev = state.currentValue
    }

    HapticSwipeToDismissBox(
        backgroundContent = {
            DeletableVaultItemBackground(
                state = state,
                modifier = Modifier.fillMaxSize()
            )
        },
        modifier = Modifier
            .clip(CardDefaults.shape)
            .then(modifier),
        state = state
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
fun DeletableVaultItemBackground(state: SwipeToDismissBoxState, modifier: Modifier = Modifier) {
    val backgroundContainerColor by animateColorAsState(
        when (state.targetValue) {
            SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        label = "DeletableKeyGoElementBackground boxBackgroundContainerColor"
    )

    val contentColor by animateColorAsState(
        when (state.targetValue) {
            SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.onErrorContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "DeletableKeyGoElementBackground contentColor"
    )

    Row(
        modifier = modifier
            .background(backgroundContainerColor)
            .padding(16.dp /*ListItemEndPadding*/),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.DeleteForever,
                contentDescription = null,
                tint = contentColor
            )
        }
    }
}

@Preview
@Composable
private fun VaultItemPreview() {
    MaterialTheme {
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