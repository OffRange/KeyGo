package de.davis.keygo.feature.item.create.presentation.login

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
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
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.DomainInfo
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.item.domain.model.Tag
import de.davis.keygo.core.item.domain.model.Vault
import de.davis.keygo.core.item.domain.model.VaultMetadata
import de.davis.keygo.core.item.presentation.StrengthIndicator
import de.davis.keygo.core.ui.composition.LocalIsInSinglePaneMode
import de.davis.keygo.core.ui.theme.KeyGoTheme
import de.davis.keygo.feature.item.core.presentation.component.ChipFormGroup
import de.davis.keygo.feature.item.core.presentation.component.KeyGoFormField
import de.davis.keygo.feature.item.core.presentation.component.gatherPendingItems
import de.davis.keygo.feature.item.core.presentation.transformation.rememberSchemeStrippingTransformation
import de.davis.keygo.feature.item.create.R
import de.davis.keygo.feature.item.create.presentation.component.FormGroup
import de.davis.keygo.feature.item.create.presentation.component.KeyGoItemForm
import de.davis.keygo.feature.item.create.presentation.component.OverrideTotpDialog
import de.davis.keygo.feature.item.create.presentation.component.SelectItemForTotpModificationDialog
import de.davis.keygo.feature.item.create.presentation.component.TAG_DELIMITERS
import de.davis.keygo.feature.item.create.presentation.component.TotpParseErrorDialog
import de.davis.keygo.feature.item.create.presentation.login.model.DialogState
import de.davis.keygo.feature.item.create.presentation.login.model.LoginBaseState
import de.davis.keygo.feature.item.create.presentation.login.model.LoginUiEvent
import de.davis.keygo.feature.item.create.presentation.login.model.LoginUiState
import de.davis.keygo.feature.item.create.presentation.model.VaultsState
import de.davis.keygo.feature.item.create.presentation.password.GeneratePasswordModalBottomSheet
import de.davis.keygo.feature.totp.presentation.component.QRScanner
import de.davis.keygo.core.item.R as CoreItemR
import de.davis.keygo.core.ui.R as CoreUiR
import de.davis.keygo.feature.item.core.R as ItemCoreR

@Composable
internal fun LoginContent(state: LoginUiState, onEvent: (LoginUiEvent) -> Unit) {
    when (state) {
        LoginUiState.Loading -> LoginLoadingScaffold(
            onBackClick = { onEvent(LoginUiEvent.OnBackClick) },
        )

        is LoginUiState.Ready -> LoginReadyContent(
            state = state.base,
            vaultsState = state.vaultsState,
            onEvent = onEvent,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoginLoadingScaffold(onBackClick: () -> Unit) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            LoginTopAppBar(
                updating = false,
                onBackClick = onBackClick,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            ContainedLoadingIndicator()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoginReadyContent(
    state: LoginBaseState,
    vaultsState: VaultsState,
    onEvent: (LoginUiEvent) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val domainTextFieldState = rememberTextFieldState()
    val tagsTextFieldState = rememberTextFieldState()
    val schemeTransformation = rememberSchemeStrippingTransformation()
    val detectedScheme by schemeTransformation.detectedScheme
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            LoginTopAppBar(
                updating = state.updating,
                onBackClick = { onEvent(LoginUiEvent.OnBackClick) },
                actions = {
                    IconButton(
                        onClick = {
                            domainTextFieldState.gatherPendingItems(DELIMITERS) {
                                onEvent(LoginUiEvent.OnAddDomains(it))
                            }

                            tagsTextFieldState.gatherPendingItems(TAG_DELIMITERS) { strings ->
                                onEvent(
                                    LoginUiEvent.OnAddTags(
                                        strings.mapNotNullTo(mutableSetOf(), Tag::of),
                                    ),
                                )
                            }

                            onEvent(LoginUiEvent.OnSubmit)
                        },
                        enabled = state.canSave,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Done,
                            contentDescription = stringResource(R.string.submit_content_description),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        }
    ) { innerPadding ->
        KeyGoItemForm(
            nameTextFieldState = state.nameTextFieldState,
            notesTextFieldState = state.notesTextFieldState,
            tagsTextFieldState = tagsTextFieldState,
            tagsForSuggestions = state.tagsForSuggestion,
            assignedTags = state.itemAssignedTags,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .padding(8.dp)
                .imePadding()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            nameError = state.nameError,
            nameExists = state.nameExists,
            vaultsState = vaultsState,
            onVaultSelect = { onEvent(LoginUiEvent.OnVaultSelected(it)) },
            onTagSubmitted = { onEvent(LoginUiEvent.OnAddTags(it)) },
            onDeleteTag = { onEvent(LoginUiEvent.OnRemoveTag(it)) },
        ) {
            item(key = "password_information") {
                var forceCompact by rememberSaveable { mutableStateOf(false) }

                FormGroup(
                    title = stringResource(CoreItemR.string.password),
                    modifier = Modifier,
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
                                    onClick = { onEvent(LoginUiEvent.OnGeneratePasswordClick) },
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = stringResource(R.string.generate_password_content_description),
                                    )
                                }
                            },
                            inputTransformation = null,
                        )

                        StrengthIndicator(
                            passwordScore = state.strengthScore,
                            forceCompact = forceCompact,
                        )
                    }

                    KeyGoFormField(
                        state = state.totpTextFieldState,
                        label = { Text(text = stringResource(R.string.totp_secret)) },
                        placeholder = { Text(text = stringResource(R.string.totp_secret)) },
                        outsideTrailingContent = {
                            IconButton(
                                onClick = { onEvent(LoginUiEvent.OnScanCodeRequest) },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = null,
                                )
                            }
                        },
                        isSecure = true,
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
                    onEdit = { old, newDomains ->
                        onEvent(LoginUiEvent.OnDeleteDomain(old.value))
                        onEvent(LoginUiEvent.OnAddDomains(newDomains))
                    },
                    containsForInput = {
                        state.domains.any { domain -> domain.value == it }
                    },
                    onSubmit = {
                        onEvent(LoginUiEvent.OnAddDomains(it))
                    },
                    onDelete = {
                        onEvent(LoginUiEvent.OnDeleteDomain(it.value))
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
                    },
                )
            }
        }

        when (state.dialogState) {
            DialogState.None -> {
                // No dialog to show
            }

            DialogState.TotpParseError -> {
                TotpParseErrorDialog(
                    onDismiss = { onEvent(LoginUiEvent.OnTotpParseErrorDismiss) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            is DialogState.SelectItemForModification -> {
                SelectItemForTotpModificationDialog(
                    onDismissRequest = {
                        // Don't allow dismissal
                    },
                    items = state.dialogState.items,
                    onItemClicked = { item ->
                        onEvent(LoginUiEvent.OnTotpModificationItemSelected(item.id))
                    },
                    onCreateNew = { onEvent(LoginUiEvent.OnCreateNewItemForTotp) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            is DialogState.OverrideTotp -> {
                OverrideTotpDialog(
                    onDismissRequest = {
                        // Don't allow dismissal
                    },
                    overrideFields = state.dialogState.fields,
                    onOverride = {
                        onEvent(LoginUiEvent.OnOverrideTotpFieldsConfirmed)
                    },
                    onKeep = {
                        onEvent(LoginUiEvent.OnOverrideTotpFieldsKept)
                    },
                    onFieldClicked = {
                        onEvent(LoginUiEvent.OnOverrideFieldClicked(it))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (state.generatePasswordBottomSheetVisible) {
        GeneratePasswordModalBottomSheet(
            onGenerated = { onEvent(LoginUiEvent.OnPasswordGenerated(it)) },
            onDismiss = { onEvent(LoginUiEvent.OnCloseBottomSheet) },
        )
    }

    if (state.scanning) {
        QRScanner(
            onClose = { onEvent(LoginUiEvent.OnBackClick) },
            success = {
                onEvent(LoginUiEvent.OnCodesScanned(it))
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoginTopAppBar(
    updating: Boolean,
    onBackClick: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    MediumFlexibleTopAppBar(
        title = {
            Text(
                text = stringResource(
                    when {
                        updating -> R.string.update_item
                        else -> CoreUiR.string.create_new_item
                    },
                ),
            )
        },
        subtitle = {
            Text(text = stringResource(CoreItemR.string.password))
        },
        navigationIcon = {
            if (LocalIsInSinglePaneMode.current) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Default.ArrowBack,
                        contentDescription = stringResource(ItemCoreR.string.back_content_description),
                    )
                }
            }
        },
        actions = actions,
        scrollBehavior = scrollBehavior,
    )
}

private val DELIMITERS = setOf(',', ' ')

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Composable
private fun LoginContentPreview() {
    val selectedVaultId = newVaultId()
    KeyGoTheme {
        LoginContent(
            state = LoginUiState.Ready(
                base = LoginBaseState(
                    strengthScore = PasswordScore.Weak,
                    domains = setOf(
                        DomainInfo(
                            loginId = newItemId(),
                            value = "example.com",
                            eTLD1 = "example.com",
                        ),
                    ),
                    nameExists = true,
                ),
                vaultsState = VaultsState(
                    vaults = listOf(
                        VaultMetadata(
                            vaultId = selectedVaultId,
                            name = "Vault 1",
                            icon = Vault.Icon.Default,
                        ),
                    ),
                    selectedVaultId = selectedVaultId,
                ),
            ),
            onEvent = {},
        )
    }
}
