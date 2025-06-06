package de.davis.keygo.item.presentation.password

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.davis.keygo.R
import de.davis.keygo.core.domain.model.Score
import de.davis.keygo.core.presentation.component.StrengthIndicator
import de.davis.keygo.item.presentation.component.KeyGoCard
import de.davis.keygo.item.presentation.component.KeyGoCardProp
import de.davis.keygo.item.presentation.password.model.GeneratePasswordUiEvent
import de.davis.keygo.item.presentation.password.model.GeneratePasswordUiState
import de.davis.keygo.item.presentation.password.model.UiCharacterSet
import de.davis.keygo.item.presentation.password.model.UiPassword
import de.davis.keygo.item.presentation.password.model.UiPassword.Companion.asUiPassword

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratePasswordContent(
    state: GeneratePasswordUiState,
    onEvent: (GeneratePasswordUiEvent) -> Unit = {},
    containerColor: Color = MaterialTheme.colorScheme.surface,
) {
    val cardProp = KeyGoCardProp.outlined(containerColor = containerColor)

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, bottom = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "title") {
            Text(
                text = stringResource(R.string.generate_password),
                style = MaterialTheme.typography.headlineSmall,
            )
        }

        item(key = "caution") {
            AnimatedVisibility(
                visible = state.showCaution,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
                modifier = Modifier
                    .clip(KeyGoCardProp.elevated().shape)
            ) {
                KeyGoCard(
                    title = {
                        Text(text = stringResource(R.string.caution))
                    },
                    modifier = Modifier
                        .fillMaxWidth(),
                    prop = KeyGoCardProp.elevated(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    leadingItem = {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null
                        )
                    }
                ) {
                    Text(text = stringResource(R.string.consider_all_char_sets))
                }
            }
        }

        stickyHeader(key = "password") {
            KeyGoCard(
                title = {
                    Text(text = stringResource(R.string.password))
                },
                modifier = Modifier
                    .fillMaxWidth(),
                prop = cardProp,
                trailingItem = {
                    IconButton(
                        onClick = { onEvent(GeneratePasswordUiEvent.OnGeneratePasswordClick) }
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                    }
                }
            ) {
                Text(
                    text = buildAnnotatedString {
                        state.generatedPassword.parts.forEach {
                            when (it) {
                                is UiPassword.Part.Letter -> append(it.text)
                                is UiPassword.Part.Number -> withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                                    append(it.text)
                                }

                                is UiPassword.Part.Symbol -> withStyle(SpanStyle(color = MaterialTheme.colorScheme.tertiary)) {
                                    append(it.text)
                                }
                            }
                        }
                    }
                )

                StrengthIndicator(
                    score = state.passwordStrength,
                    forceCompact = true
                )
            }
        }

        item(key = "length") {
            KeyGoCard(
                title = {
                    Text(
                        text = stringResource(
                            R.string.length,
                            state.sliderState.value.toInt()
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth(),
                prop = cardProp,
            ) {
                Slider(state = state.sliderState)
            }
        }

        item(key = "char_sets") {
            KeyGoCard(
                title = {
                    Text(text = stringResource(R.string.character_sets))
                },
                modifier = Modifier
                    .fillMaxWidth(),
                prop = cardProp,
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CharacterSetChip(
                        selectedCharacterSet = state.characterSet,
                        characterSet = UiCharacterSet.LOWERCASE,
                        onClick = {
                            onEvent(
                                GeneratePasswordUiEvent.OnCharacterSetClick(
                                    UiCharacterSet.LOWERCASE
                                )
                            )
                        }
                    ) {
                        Text(text = stringResource(R.string.lowercase))
                    }

                    CharacterSetChip(
                        selectedCharacterSet = state.characterSet,
                        characterSet = UiCharacterSet.UPPERCASE,
                        onClick = {
                            onEvent(
                                GeneratePasswordUiEvent.OnCharacterSetClick(
                                    UiCharacterSet.UPPERCASE
                                )
                            )
                        }
                    ) {
                        Text(text = stringResource(R.string.uppercase))
                    }

                    CharacterSetChip(
                        selectedCharacterSet = state.characterSet,
                        characterSet = UiCharacterSet.DIGITS,
                        onClick = {
                            onEvent(
                                GeneratePasswordUiEvent.OnCharacterSetClick(
                                    UiCharacterSet.DIGITS
                                )
                            )
                        }
                    ) {
                        Text(text = stringResource(R.string.digits))
                    }

                    CharacterSetChip(
                        selectedCharacterSet = state.characterSet,
                        characterSet = UiCharacterSet.PUNCTUATIONS,
                        onClick = {
                            onEvent(
                                GeneratePasswordUiEvent.OnCharacterSetClick(
                                    UiCharacterSet.PUNCTUATIONS
                                )
                            )
                        }
                    ) {
                        Text(text = stringResource(R.string.punctuations))
                    }
                }
            }
        }

        item(key = "buttons") {
            Box(
                modifier = Modifier
                    .fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Button(
                    onClick = { onEvent(GeneratePasswordUiEvent.OnUseClick) }
                ) {
                    Text(text = stringResource(R.string.use_generated_password))
                }
            }
        }
    }
}

@Composable
fun CharacterSetChip(
    selectedCharacterSet: UiCharacterSet,
    characterSet: UiCharacterSet,
    onClick: () -> Unit,
    label: @Composable () -> Unit
) {
    FilterChip(
        selected = selectedCharacterSet.selected(characterSet),
        label = label,
        onClick = onClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun GeneratePasswordBottomSheetPreview() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            GeneratePasswordContent(
                state = GeneratePasswordUiState(
                    generatedPassword = "p@ssw0rd".asUiPassword(),
                    passwordStrength = Score.Ridiculous,
                    showCaution = true,
                )
            )
        }
    }
}