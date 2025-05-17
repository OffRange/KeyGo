package de.davis.keygo.item.presentation.password

import android.annotation.SuppressLint
import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.davis.keygo.R
import de.davis.keygo.core.presentation.theme.KeyGoTheme
import de.davis.keygo.item.domain.model.Score
import de.davis.keygo.item.presentation.component.FormField
import de.davis.keygo.item.presentation.component.FormGroup
import de.davis.keygo.item.presentation.component.KeyGoItemForm
import de.davis.keygo.item.presentation.password.model.PasswordUiEvent
import de.davis.keygo.item.presentation.password.model.PasswordUiState

@Composable
fun PasswordContent(state: PasswordUiState, onEvent: (PasswordUiEvent) -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(WindowInsets.systemBars.asPaddingValues())
    ) {
        KeyGoItemForm(
            nameTextFieldState = state.nameTextFieldState,
            notesTextFieldState = state.notesTextFieldState,
            modifier = Modifier
                .padding(8.dp)
                .consumeWindowInsets(WindowInsets.systemBars)
        ) {
            item(key = "password_information") {
                var forceCompact by rememberSaveable { mutableStateOf(false) }

                FormGroup(
                    title = stringResource(R.string.password),
                    modifier = Modifier
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FormField(
                            state = state.passwordTextFieldState,
                            label = { Text(text = stringResource(R.string.password)) },
                            modifier = Modifier.onFocusChanged {
                                forceCompact = !it.hasFocus
                            },
                            placeholder = { Text(text = stringResource(R.string.password)) },
                            isSecure = true,
                            outsideTrailingContent = {
                                IconButton(
                                    onClick = { onEvent(PasswordUiEvent.OnGeneratePasswordClick) },
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = stringResource(R.string.generate_password_content_description)
                                    )
                                }
                            }
                        )

                        StrengthIndicator(
                            score = state.strengthScore,
                            forceCompact = forceCompact,
                        )
                    }

                    FormField(
                        state = state.usernameTextFieldState,
                        label = { Text(text = stringResource(R.string.username)) },
                        placeholder = { Text(text = stringResource(R.string.username)) },
                    )
                    FormField(
                        state = state.websiteTextFieldState,
                        label = { Text(text = stringResource(R.string.website)) },
                        placeholder = { Text(text = stringResource(R.string.website)) },
                    )
                }
            }
        }
    }
}

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
        verticalAlignment = Alignment.CenterVertically,
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
                    modifier = Modifier
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

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Composable
private fun PasswordContentPreview() {
    KeyGoTheme {
        PasswordContent(
            state = PasswordUiState(
                strengthScore = Score.Weak
            ),
            onEvent = {}
        )
    }
}