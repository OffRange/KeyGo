package de.davis.keygo.feature.item.create.presentation.password

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.davis.keygo.core.item.domain.model.DomainInfo
import de.davis.keygo.core.item.domain.model.Password
import de.davis.keygo.core.item.presentation.StrengthIndicator
import de.davis.keygo.core.ui.composition.LocalIsInSinglePaneMode
import de.davis.keygo.core.ui.theme.KeyGoTheme
import de.davis.keygo.feature.item.core.presentation.component.ChipFormGroup
import de.davis.keygo.feature.item.core.presentation.component.KeyGoFormField
import de.davis.keygo.feature.item.core.presentation.transformation.rememberSchemeStrippingTransformation
import de.davis.keygo.feature.item.create.R
import de.davis.keygo.feature.item.create.presentation.component.FormGroup
import de.davis.keygo.feature.item.create.presentation.component.KeyGoItemForm
import de.davis.keygo.feature.item.create.presentation.component.OverrideTotpDialog
import de.davis.keygo.feature.item.create.presentation.component.SelectItemForTotpModificationDialog
import de.davis.keygo.feature.item.create.presentation.component.TotpParseErrorDialog
import de.davis.keygo.feature.item.create.presentation.password.model.DialogState
import de.davis.keygo.feature.item.create.presentation.password.model.PasswordUiEvent
import de.davis.keygo.feature.item.create.presentation.password.model.PasswordUiState
import de.davis.keygo.feature.totp.presentation.component.QRScanner
import de.davis.keygo.core.item.R as CoreItemR
import de.davis.keygo.core.ui.R as CoreUiR
import de.davis.keygo.feature.item.core.R as ItemCoreR

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PasswordContent(state: PasswordUiState, onEvent: (PasswordUiEvent) -> Unit) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val domainTextFieldState = rememberTextFieldState()
    val schemeTransformation = rememberSchemeStrippingTransformation()
    val detectedScheme by schemeTransformation.detectedScheme
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            MediumFlexibleTopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            when {
                                state.updating -> R.string.update_item
                                else -> CoreUiR.string.create_new_item
                            }
                        )
                    )
                },
                subtitle = {
                    Text(text = stringResource(CoreItemR.string.password))
                },
                navigationIcon = {
                    if (LocalIsInSinglePaneMode.current) {
                        IconButton(onClick = { onEvent(PasswordUiEvent.OnBackClick) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Default.ArrowBack,
                                contentDescription = stringResource(ItemCoreR.string.back_content_description)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val pending = domainTextFieldState.text.toString()
                            .split(delimiters = DELIMITERS.toCharArray())
                            .filter { it.isNotBlank() }
                            .toSet()
                        if (pending.isNotEmpty()) {
                            onEvent(PasswordUiEvent.OnAddDomains(pending))
                        }
                        onEvent(PasswordUiEvent.OnSubmit)
                    }) {
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
                    title = stringResource(CoreItemR.string.password),
                    modifier = Modifier
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        KeyGoFormField(
                            state = state.passwordTextFieldState,
                            label = { Text(text = stringResource(CoreItemR.string.password)) },
                            modifier = Modifier.onFocusChanged {
                                forceCompact = !it.hasFocus
                            },
                            placeholder = { Text(text = stringResource(CoreItemR.string.password)) },
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
                        label = { Text(text = stringResource(ItemCoreR.string.login_identifier)) },
                        placeholder = { Text(text = stringResource(ItemCoreR.string.login_identifier)) },
                    )
                }
            }

            item(key = "domain_information") {
                ChipFormGroup(
                    title = stringResource(R.string.domain_information),
                    items = state.domains,
                    textOf = { it.value },
                    state = domainTextFieldState,
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
                    ),
                    delimiters = DELIMITERS,
                    inputTransformation = schemeTransformation,
                    prefix = {
                        Text(text = detectedScheme ?: "https://")
                    }
                ) { item ->
                    MenuChip(
                        chipText = item.value,
                        onDeleteClick = {
                            onEvent(PasswordUiEvent.OnDeleteDomain(item.value))
                        },
                        onModifyClick = {
                            // TODO:
                            //  1. when the user does not submit the new domain, rollback to the old domain
                            //  2. when the user clears the text-field and the focus is lost, rollback to old domain
                            onEvent(PasswordUiEvent.OnDeleteDomain(item.value))
                            domainTextFieldState.setTextAndPlaceCursorAtEnd(item.value)
                        }
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
        GeneratePasswordModalBottomSheet(
            onGenerated = { onEvent(PasswordUiEvent.OnPasswordGenerated(it)) },
            onDismiss = { onEvent(PasswordUiEvent.OnCloseBottomSheet) }
        )
    }

    if (state.scanning) {
        QRScanner(
            onClose = { onEvent(PasswordUiEvent.OnBackClick) },
            success = {
                onEvent(PasswordUiEvent.OnCodesScanned(it))
            }
        )
    }
}


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MenuChip(
    chipText: String,
    onDeleteClick: () -> Unit,
    onModifyClick: () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = Modifier.wrapContentSize(Alignment.TopStart)
    ) {
        InputChip(
            selected = false,
            onClick = { expanded = !expanded },
            label = { Text(text = chipText) }
        )

        DropdownMenuPopup(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 175.dp)
        ) {
            DropdownMenuGroup(
                shapes = MenuDefaults.groupShape(0, 1),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                DropdownMenuItem(
                    onClick = onModifyClick,
                    text = { Text(text = stringResource(R.string.edit)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                            contentDescription = null
                        )
                    },
                    shape = MenuDefaults.itemShape(0, 2).shape,
                )
                DropdownMenuItem(
                    onClick = onDeleteClick,
                    text = { Text(text = stringResource(R.string.delete)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                            contentDescription = null
                        )
                    },
                    shape = MenuDefaults.itemShape(1, 2).shape,
                )
            }
        }
    }
}

private val DELIMITERS = setOf(',', ' ')

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Composable
private fun PasswordContentPreview() {
    KeyGoTheme {
        PasswordContent(
            state = PasswordUiState(
                strengthScore = Password.Score.Weak,
                domains = setOf(
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
