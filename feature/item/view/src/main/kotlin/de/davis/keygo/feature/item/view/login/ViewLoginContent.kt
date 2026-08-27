package de.davis.keygo.feature.item.view.login

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.text.input.then
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreTime
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.domain.model.DomainInfo
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.item.presentation.StrengthIndicator
import de.davis.keygo.core.item.presentation.toImageVector
import de.davis.keygo.core.ui.composition.LocalIsInSinglePaneMode
import de.davis.keygo.feature.item.core.presentation.component.CopyToClipboardButton
import de.davis.keygo.feature.item.core.presentation.component.KeyGoFormField
import de.davis.keygo.feature.item.core.presentation.component.KeyGoFormSuggestionField
import de.davis.keygo.feature.item.core.presentation.entry
import de.davis.keygo.feature.item.core.presentation.login.model.FieldType
import de.davis.keygo.feature.item.core.presentation.login.model.UiPassword.Companion.asUiPassword
import de.davis.keygo.feature.item.core.presentation.login.model.colored
import de.davis.keygo.feature.item.core.presentation.transformation.TrimTransformation
import de.davis.keygo.feature.item.core.presentation.transformation.rememberSchemeStrippingTransformation
import de.davis.keygo.feature.item.view.R
import de.davis.keygo.feature.item.view.login.model.ModificationDialog
import de.davis.keygo.feature.item.view.login.model.ObfuscatedString
import de.davis.keygo.feature.item.view.login.model.TotpState
import de.davis.keygo.feature.item.view.login.model.ViewLoginState
import de.davis.keygo.feature.item.view.login.model.ViewLoginUiEvent
import de.davis.keygo.feature.item.view.onHold
import de.davis.keygo.feature.totp.domain.model.TotpValue
import de.davis.keygo.feature.totp.presentation.component.QRScanner
import de.davis.keygo.feature.totp.presentation.component.TotpParseErrorDialog
import de.davis.keygo.core.item.R as CoreItemR
import de.davis.keygo.core.ui.R as CoreUiR
import de.davis.keygo.feature.item.core.R as ItemCoreR

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ViewLoginContent(state: ViewLoginState, onEvent: (ViewLoginUiEvent) -> Unit) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        topBar = {
            MediumFlexibleTopAppBar(
                title = {
                    Text(text = state.name)
                },
                subtitle = {
                    state.vaultMetadata?.let { metadata ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CompositionLocalProvider(
                                LocalContentColor provides MaterialTheme.colorScheme.onSecondaryContainer
                            ) {
                                val textStyle = LocalTextStyle.current
                                val size = with(LocalDensity.current) {
                                    if (textStyle.fontSize.isSp) textStyle.fontSize.toDp()
                                    else 24.dp
                                }
                                Icon(
                                    imageVector = metadata.icon.toImageVector(),
                                    contentDescription = null,
                                    modifier = Modifier.size(size),
                                )
                                Text(text = metadata.name)
                            }
                            Text(text = "\u2022")
                            Text(text = stringResource(CoreItemR.string.login))
                        }
                    }
                },
                navigationIcon = {
                    if (LocalIsInSinglePaneMode.current) {
                        IconButton(onClick = { onEvent(ViewLoginUiEvent.OnBackClick) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Default.ArrowBack,
                                contentDescription = stringResource(ItemCoreR.string.back_content_description),
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onEvent(ViewLoginUiEvent.OnPinClick) },
                    ) {
                        Icon(
                            imageVector = if (state.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = null,
                        )
                    }

                    IconButton(
                        onClick = { onEvent(ViewLoginUiEvent.OnEditRequest) },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(ItemCoreR.string.edit_content_description),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        }
    ) { innerPadding ->
        val name = stringResource(ItemCoreR.string.name)
        val passkey = stringResource(ItemCoreR.string.passkey)
        val password = stringResource(CoreItemR.string.password)
        val totp = stringResource(ItemCoreR.string.totp)
        val username = stringResource(ItemCoreR.string.login_identifier)
        val domains = stringResource(ItemCoreR.string.domains)
        val tags = stringResource(ItemCoreR.string.tags)
        val note = stringResource(ItemCoreR.string.note)

        var isPasswordHidden by rememberSaveable { mutableStateOf(true) }

        val progress = remember { Animatable(1f) }
        val totpState = state.totpState
        LaunchedEffect(totpState) {
            when (totpState) {
                is TotpState.HasTotp -> {
                    val remaining = totpState.value.validUntil - System.currentTimeMillis()

                    progress.snapTo(remaining / totpState.value.maxLifetime.toFloat())
                    progress.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(
                            durationMillis = remaining.toInt(),
                            easing = LinearEasing,
                        ),
                    )
                }

                else -> {}
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .padding(start = 8.dp, end = 8.dp, top = 8.dp)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            entry(
                title = name,
                leadingIcon = Icons.Default.Badge,
            ) {
                Text(text = state.name)
            }

            if (state.passkeyRPs.isNotEmpty()) {
                entry(
                    title = passkey,
                    leadingIcon = Icons.Default.Key,
                ) {
                    Text(text = state.passkeyRPs.joinToString(", "))
                }
            }

            val pwd = state.password
            val score = state.passwordStrengthScore
            if (pwd != null && score != null) {
                entry(
                    title = password,
                    leadingIcon = Icons.Default.Password,
                    modifier = Modifier.onHold {
                        isPasswordHidden = !it
                    },
                    trailingContent = {
                        CopyToClipboardButton(pwd.raw)
                    },
                ) {
                    val scrollState = rememberScrollState()
                    Text(
                        text = if (isPasswordHidden) AnnotatedString(pwd.hidden)
                        else pwd.raw.asUiPassword().colored(),
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        modifier = Modifier.horizontalScroll(scrollState),
                    )
                    StrengthIndicator(
                        passwordScore = score,
                        forceCompact = true,
                    )
                }
            }

            when (totpState) {
                is TotpState.HasTotp -> entry(
                    title = totp,
                    leadingIcon = Icons.Default.AccessTime,
                    trailingContent = {
                        CopyToClipboardButton(state.totpState.value.code)
                    },
                ) {
                    Text(text = state.totpState.formattedCode)
                    LinearProgressIndicator(
                        progress = { progress.value },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                is TotpState.Error -> entry(
                    title = totp,
                    leadingIcon = Icons.Default.Error,
                ) {
                    Text(text = totpState.error.message)
                }

                else -> {}
            }

            if (state.username.isNotBlank()) {
                entry(
                    title = username,
                    leadingIcon = Icons.Default.Person,
                ) {
                    Text(text = state.username)
                }
            }

            if (state.domains.isNotEmpty()) {
                entry(
                    title = domains,
                    leadingIcon = Icons.Default.Link,
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.domains.forEach {
                            key(it.value) {
                                AssistChip(
                                    onClick = {
                                        onEvent(ViewLoginUiEvent.OpenWebsite(it.value))
                                    },
                                    label = { Text(text = it.value) },
                                )
                            }
                        }
                    }
                }
            }

            if (state.tags.isNotEmpty()) {
                entry(
                    title = tags,
                    leadingIcon = Icons.Default.Sell
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.tags.forEach {
                            key(it.display) {
                                AssistChip(
                                    onClick = {},
                                    label = { Text(text = it.display) },
                                )
                            }
                        }
                    }
                }
            }

            if (state.note.isNotBlank()) {
                entry(
                    title = note,
                    leadingIcon = Icons.AutoMirrored.Default.Notes,
                ) {
                    Text(text = state.note)
                }
            }

            item(key = "actions") {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.totpState is TotpState.NoTotp) {
                        AddChip(
                            fieldType = FieldType.Totp,
                            onClick = { onEvent(ViewLoginUiEvent.OnModifyFieldRequest(it)) },
                        )
                    }
                    if (state.username.isBlank()) {
                        AddChip(
                            fieldType = FieldType.Username,
                            onClick = { onEvent(ViewLoginUiEvent.OnModifyFieldRequest(it)) },
                        )
                    }

                    AddChip(
                        fieldType = FieldType.Domain,
                        onClick = { onEvent(ViewLoginUiEvent.OnModifyFieldRequest(it)) },
                    )

                    AddChip(
                        fieldType = FieldType.Tag,
                        onClick = { onEvent(ViewLoginUiEvent.OnModifyFieldRequest(it)) },
                    )

                    if (state.note.isBlank()) {
                        AddChip(
                            fieldType = FieldType.Note,
                            onClick = { onEvent(ViewLoginUiEvent.OnModifyFieldRequest(it)) },
                        )
                    }
                }
            }
        }

        state.modificationDialog?.let { dialog ->
            val textFieldInputState = rememberTextFieldState(dialog.initialValue)
            AlertDialog(
                onDismissRequest = { onEvent(ViewLoginUiEvent.OnCloseDialog) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onEvent(
                                ViewLoginUiEvent.OnSubmitModification(
                                    textFieldInputState.text.toString(),
                                ),
                            )
                        },
                    ) {
                        Text(text = stringResource(CoreUiR.string.add))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                icon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                    )
                },
                title = {
                    Text(text = stringResource(CoreUiR.string.add))
                },
                text = {
                    when (dialog.fieldType) {
                        FieldType.Tag -> KeyGoFormSuggestionField(
                            suggestions = dialog.tagsToSuggest.mapTo(mutableSetOf()) { it.display },
                            onSuggestionSelected = {
                                textFieldInputState.setTextAndPlaceCursorAtEnd(
                                    it
                                )
                            },
                            state = textFieldInputState,
                            label = {
                                Text(text = dialog.fieldType.addLabel())
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        else -> {
                            val schemeTransformation = rememberSchemeStrippingTransformation()
                            val detectedScheme by schemeTransformation.detectedScheme

                            val transformation = when {
                                dialog.fieldType == FieldType.Domain ->
                                    TrimTransformation.then(schemeTransformation)

                                !dialog.fieldType.isSensitive -> TrimTransformation
                                else -> null
                            }

                            KeyGoFormField(
                                state = textFieldInputState,
                                label = {
                                    Text(text = dialog.fieldType.addLabel())
                                },
                                modifier = Modifier.fillMaxWidth(),
                                prefix = if (dialog.fieldType == FieldType.Domain) {
                                    { Text(text = detectedScheme ?: "https://") }
                                } else null,
                                outsideTrailingContent = if (dialog.fieldType == FieldType.Totp) {
                                    {
                                        IconButton(
                                            onClick = { onEvent(ViewLoginUiEvent.OnScanCodeRequest) },
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.QrCodeScanner,
                                                contentDescription = null,
                                            )
                                        }
                                    }
                                } else null,
                                error = dialog.error,
                                isSecure = dialog.fieldType.isSensitive,
                                inputTransformation = transformation,
                            )
                        }
                    }
                },
            )
        }
    }

    if (state.totpParseError) {
        TotpParseErrorDialog(
            onDismiss = { onEvent(ViewLoginUiEvent.OnTotpParseErrorDismiss) },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (state.scanning) {
        QRScanner(
            onClose = { onEvent(ViewLoginUiEvent.OnBackClick) },
            success = {
                onEvent(ViewLoginUiEvent.OnCodesScanned(it))
            },
        )
    }
}

@Composable
private fun AddChip(fieldType: FieldType, onClick: (FieldType) -> Unit) {
    AssistChip(
        onClick = {
            onClick(fieldType)
        },
        label = { Text(text = fieldType.addLabel()) },
        leadingIcon = {
            Icon(
                imageVector = fieldType.addIcon(),
                contentDescription = null,
            )
        },
    )
}

@Composable
private fun FieldType.addLabel(): String {
    return when (this) {
        FieldType.Name -> stringResource(ItemCoreR.string.name)
        FieldType.Password -> stringResource(CoreItemR.string.password)
        FieldType.Totp -> stringResource(R.string.add_totp)
        FieldType.Username -> stringResource(R.string.add_username)
        FieldType.Domain -> stringResource(R.string.add_domain)
        FieldType.Tag -> stringResource(R.string.add_tag)
        FieldType.Note -> stringResource(R.string.add_note)
    }
}

@Composable
private fun FieldType.addIcon(): ImageVector {
    return when (this) {
        FieldType.Name -> Icons.Default.Badge
        FieldType.Password -> Icons.Default.Password
        FieldType.Totp -> Icons.Default.MoreTime
        FieldType.Username -> Icons.Default.PersonAdd
        FieldType.Domain -> Icons.Default.AddLink
        FieldType.Tag -> Icons.Default.Sell
        FieldType.Note -> Icons.AutoMirrored.Default.NoteAdd
    }
}

@Preview
@Composable
private fun ViewLoginContentPreview() {
    MaterialTheme {
        CompositionLocalProvider(
            LocalIsInSinglePaneMode provides true
        ) {
            ViewLoginContent(
                state = ViewLoginState(
                    name = "Login 1",
                    passkeyRPs = listOf("example.com", "example.org"),
                    password = ObfuscatedString("Password"),
                    passwordStrengthScore = PasswordScore.Ridiculous,
                    totpState = TotpState.HasTotp(
                        TotpValue(
                            code = "123456",
                            validUntil = System.currentTimeMillis() + 30_000L,
                            maxLifetime = 30_000L,
                        )
                    ),
                    username = "Username 1",
                    domains = setOf(
                        DomainInfo(
                            loginId = newItemId(),
                            value = "login.example.com",
                            eTLD1 = "example.com",
                        ),
                    ),
                    note = "Note about the login or any additional information that might be useful.",
                ),
                onEvent = {},
            )
        }
    }
}

@Preview
@Composable
private fun ViewLoginContentModificationDialogPreview() {
    MaterialTheme {
        CompositionLocalProvider(
            LocalIsInSinglePaneMode provides true
        ) {
            ViewLoginContent(
                state = ViewLoginState(
                    name = "Login 1",
                    password = ObfuscatedString("Password"),
                    passwordStrengthScore = PasswordScore.Ridiculous,
                    username = "Username 1",
                    domains = setOf(
                        DomainInfo(
                            loginId = newItemId(),
                            value = "login.example.com",
                            eTLD1 = "example.com",
                        ),
                    ),
                    modificationDialog = ModificationDialog(
                        fieldType = FieldType.Name,
                        initialValue = "Login",
                    ),
                ),
                onEvent = {},
            )
        }
    }
}
