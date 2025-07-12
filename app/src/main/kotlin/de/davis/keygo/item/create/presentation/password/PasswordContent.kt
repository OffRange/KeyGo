package de.davis.keygo.item.create.presentation.password

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.davis.keygo.R
import de.davis.keygo.core.domain.model.Score
import de.davis.keygo.core.presentation.LocalIsInSinglePaneMode
import de.davis.keygo.core.presentation.component.KeyGoFormField
import de.davis.keygo.core.presentation.component.StrengthIndicator
import de.davis.keygo.core.presentation.theme.KeyGoTheme
import de.davis.keygo.item.create.presentation.component.FormGroup
import de.davis.keygo.item.create.presentation.component.KeyGoItemForm
import de.davis.keygo.item.create.presentation.password.model.PasswordUiEvent
import de.davis.keygo.item.create.presentation.password.model.PasswordUiState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PasswordContent(state: PasswordUiState, onEvent: (PasswordUiEvent) -> Unit) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            MediumFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(R.string.create_new_item))
                },
                subtitle = {
                    Text(text = stringResource(R.string.password))
                },
                navigationIcon = {
                    if (LocalIsInSinglePaneMode.current) {
                        IconButton(onClick = { onEvent(PasswordUiEvent.OnBackClick) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Default.ArrowBack,
                                contentDescription = stringResource(R.string.back_content_description)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { onEvent(PasswordUiEvent.OnSubmit) }) {
                        Icon(
                            imageVector = Icons.Default.Done,
                            contentDescription = stringResource(R.string.submit_content_description)
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        KeyGoItemForm(
            nameTextFieldState = state.nameTextFieldState,
            notesTextFieldState = state.notesTextFieldState,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .padding(8.dp)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            nameError = state.nameError,
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
                        KeyGoFormField(
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
                            },
                            error = state.passwordError,
                            inputTransformation = null
                        )

                        StrengthIndicator(
                            score = state.strengthScore,
                            forceCompact = forceCompact,
                        )
                    }

                    KeyGoFormField(
                        state = state.totpTextFieldState,
                        label = { Text(text = stringResource(R.string.totp_secret)) },
                        placeholder = { Text(text = stringResource(R.string.totp_secret)) },
                        isSecure = true
                    )

                    KeyGoFormField(
                        state = state.usernameTextFieldState,
                        label = { Text(text = stringResource(R.string.username)) },
                        placeholder = { Text(text = stringResource(R.string.username)) },
                    )
                    KeyGoFormField(
                        state = state.websiteTextFieldState,
                        label = { Text(text = stringResource(R.string.website)) },
                        placeholder = { Text(text = stringResource(R.string.website)) },
                    )
                }
            }
        }
    }

    if (state.generatePasswordBottomSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { onEvent(PasswordUiEvent.OnCloseBottomSheet) },
        ) {
            GeneratePasswordContent(
                state = state.generatePasswordState,
                onEvent = onEvent,
                containerColor = BottomSheetDefaults.ContainerColor,
            )
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