package de.davis.keygo.item.create.presentation.password

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.davis.keygo.R
import de.davis.keygo.core.item.domain.model.DomainInfo
import de.davis.keygo.core.item.domain.model.Password
import de.davis.keygo.core.presentation.LocalIsInSinglePaneMode
import de.davis.keygo.core.presentation.component.KeyGoFormField
import de.davis.keygo.core.presentation.component.StrengthIndicator
import de.davis.keygo.core.ui.theme.KeyGoTheme
import de.davis.keygo.item.core.presentation.component.ChipFormGroup
import de.davis.keygo.item.create.presentation.component.FormGroup
import de.davis.keygo.item.create.presentation.component.KeyGoItemForm
import de.davis.keygo.item.create.presentation.component.OverrideTotpDialog
import de.davis.keygo.item.create.presentation.component.SelectItemForTotpModificationDialog
import de.davis.keygo.item.create.presentation.component.TotpParseErrorDialog
import de.davis.keygo.item.create.presentation.password.model.DialogState
import de.davis.keygo.item.create.presentation.password.model.PasswordUiEvent
import de.davis.keygo.item.create.presentation.password.model.PasswordUiState
import de.davis.keygo.totp.presentation.component.QRScanner
import kotlinx.collections.immutable.persistentSetOf

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PasswordContent(state: PasswordUiState, onEvent: (PasswordUiEvent) -> Unit) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            MediumFlexibleTopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            when {
                                state.updating -> R.string.update_item
                                else -> R.string.create_new_item
                            }
                        )
                    )
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
                .imePadding()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            nameError = state.nameError,
            nameExists = state.nameExists,
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
                        outsideTrailingContent = {
                            IconButton(
                                onClick = { onEvent(PasswordUiEvent.OnScanCodeRequest) }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = null
                                )
                            }
                        },
                        isSecure = true
                    )

                    KeyGoFormField(
                        state = state.usernameTextFieldState,
                        label = { Text(text = stringResource(R.string.login_identifier)) },
                        placeholder = { Text(text = stringResource(R.string.login_identifier)) },
                    )
                }
            }

            item(key = "domain_information") {
                ChipFormGroup(
                    title = stringResource(R.string.domain_information),
                    items = state.domains,
                    containsForInput = {
                        state.domains.any { domain -> domain.value == it }
                    },
                    onSubmit = {
                        onEvent(PasswordUiEvent.OnAddDomains(it))
                    },
                    onDelete = {
                        onEvent(PasswordUiEvent.OnDeleteDomain(it.value))
                    },
                    label = {
                        Text(text = stringResource(R.string.add_domains))
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next
                    ),
                    prefix = {
                        Text(text = "https://")
                    }
                ) { item, selected ->
                    InputChip(
                        selected = selected,
                        onClick = { /* TODO: maybe allow editing? but definitely removing */ },
                        label = { Text(text = item.value) }
                    )
                }
            }
        }

        when (state.dialogState) {
            DialogState.None -> {
                // No dialog to show
            }

            DialogState.TotpParseError -> {
                TotpParseErrorDialog(
                    onDismiss = { onEvent(PasswordUiEvent.OnTotpParseErrorDismiss) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            is DialogState.SelectItemForModification -> {
                SelectItemForTotpModificationDialog(
                    onDismissRequest = {
                        // Don't allow dismissal
                    },
                    items = state.dialogState.items,
                    onItemClicked = { item ->
                        onEvent(PasswordUiEvent.OnTotpModificationItemSelected(item.vaultItemId))
                    },
                    onCreateNew = { onEvent(PasswordUiEvent.OnCreateNewItemForTotp) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            is DialogState.OverrideTotp -> {
                OverrideTotpDialog(
                    onDismissRequest = {
                        // Don't allow dismissal
                    },
                    overrideFields = state.dialogState.fields,
                    onOverride = {
                        onEvent(PasswordUiEvent.OnOverrideTotpFieldsConfirmed)
                    },
                    onKeep = {
                        onEvent(PasswordUiEvent.OnOverrideTotpFieldsKept)
                    },
                    onFieldClicked = {
                        onEvent(PasswordUiEvent.OnOverrideFieldClicked(it))
                    },
                    modifier = Modifier.fillMaxWidth()
                )
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

    // TODO: check permissions
    if (state.scanning) {
        QRScanner(
            onClose = { onEvent(PasswordUiEvent.OnBackClick) },
            success = {
                onEvent(PasswordUiEvent.OnCodesScanned(it))
            }
        )
    }
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Composable
private fun PasswordContentPreview() {
    KeyGoTheme {
        PasswordContent(
            state = PasswordUiState(
                strengthScore = Password.Score.Weak,
                domains = persistentSetOf(
                    DomainInfo(
                        passwordId = 0,
                        value = "example.com",
                        eTLD1 = "example.com"
                    )
                ),
                nameExists = true
            ),
            onEvent = {}
        )
    }
}