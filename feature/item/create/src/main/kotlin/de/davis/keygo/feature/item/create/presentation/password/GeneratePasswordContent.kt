package de.davis.keygo.feature.item.create.presentation.password

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.davis.keygo.core.item.presentation.StrengthIndicator
import de.davis.keygo.core.ui.components.KeyGoCard
import de.davis.keygo.core.ui.components.KeyGoCardProperties
import de.davis.keygo.core.ui.theme.secretTextStyle
import de.davis.keygo.core.util.presentation.ObserveAsEvents
import de.davis.keygo.feature.item.core.presentation.login.model.colored
import de.davis.keygo.feature.item.create.R
import de.davis.keygo.feature.item.create.presentation.password.model.GeneratePasswordUiEvent
import de.davis.keygo.feature.item.create.presentation.password.model.UiCharacterSet
import org.koin.androidx.compose.koinViewModel
import de.davis.keygo.core.item.R as CoreItemR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratePasswordContent(
    onGenerated: (String) -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.surface,
) {
    val viewModel = koinViewModel<GeneratePasswordViewModel>()
    val state by viewModel.generationState.collectAsStateWithLifecycle()

    val cardProp = KeyGoCardProperties.outlined(containerColor = containerColor)

    ObserveAsEvents(viewModel.finalPassword) {
        onGenerated(it)
    }

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
                    .clip(KeyGoCardProperties.elevated().shape)
            ) {
                KeyGoCard(
                    title = {
                        Text(text = stringResource(R.string.caution))
                    },
                    modifier = Modifier
                        .fillMaxWidth(),
                    properties = KeyGoCardProperties.elevated(containerColor = MaterialTheme.colorScheme.primaryContainer),
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
                    Text(text = stringResource(CoreItemR.string.password))
                },
                modifier = Modifier
                    .fillMaxWidth(),
                properties = cardProp,
                trailingItem = {
                    IconButton(
                        onClick = { viewModel.onEvent(GeneratePasswordUiEvent.OnGeneratePasswordClick) }
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                    }
                }
            ) {
                Text(
                    text = state.generatedPassword.colored(),
                    style = secretTextStyle,
                )

                StrengthIndicator(
                    passwordScore = state.passwordStrength,
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
                            viewModel.sliderState.value.toInt()
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth(),
                properties = cardProp,
            ) {
                Slider(state = viewModel.sliderState)
            }
        }

        item(key = "char_sets") {
            KeyGoCard(
                title = {
                    Text(text = stringResource(R.string.character_sets))
                },
                modifier = Modifier
                    .fillMaxWidth(),
                properties = cardProp,
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CharacterSetChip(
                        selectedCharacterSet = state.characterSet,
                        characterSet = UiCharacterSet.LOWERCASE,
                        onClick = {
                            viewModel.onEvent(
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
                            viewModel.onEvent(
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
                            viewModel.onEvent(
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
                            viewModel.onEvent(
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
                    onClick = { viewModel.onEvent(GeneratePasswordUiEvent.OnUseClick) }
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
