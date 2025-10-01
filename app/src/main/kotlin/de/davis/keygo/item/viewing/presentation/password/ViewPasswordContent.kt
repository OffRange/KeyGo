package de.davis.keygo.item.viewing.presentation.password

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreTime
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.davis.keygo.R
import de.davis.keygo.core.item.domain.model.DomainInfo
import de.davis.keygo.core.item.domain.model.Password
import de.davis.keygo.core.presentation.LocalIsInSinglePaneMode
import de.davis.keygo.core.presentation.component.KeyGoCard
import de.davis.keygo.core.presentation.component.KeyGoFormField
import de.davis.keygo.core.presentation.component.StrengthIndicator
import de.davis.keygo.core.presentation.transformation.TrimTransformation
import de.davis.keygo.item.core.presentation.component.CopyToClipboardButton
import de.davis.keygo.item.core.presentation.password.model.FieldType
import de.davis.keygo.item.viewing.presentation.password.model.ModificationDialog
import de.davis.keygo.item.viewing.presentation.password.model.ObfuscatedString
import de.davis.keygo.item.viewing.presentation.password.model.ViewPasswordState
import de.davis.keygo.item.viewing.presentation.password.model.ViewPasswordUiEvent
import de.davis.keygo.totp.domain.model.TotpInformation

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ViewPasswordContent(state: ViewPasswordState, onEvent: (ViewPasswordUiEvent) -> Unit) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        topBar = {
            MediumFlexibleTopAppBar(
                title = {
                    Text(text = state.name)
                },
                subtitle = {
                    Text(text = stringResource(R.string.password))
                },
                navigationIcon = {
                    if (LocalIsInSinglePaneMode.current) {
                        IconButton(onClick = { onEvent(ViewPasswordUiEvent.OnBackClick) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Default.ArrowBack,
                                contentDescription = stringResource(R.string.back_content_description)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onEvent(ViewPasswordUiEvent.OnEditRequest) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(R.string.edit_content_description)
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        val name = stringResource(R.string.name)
        val password = stringResource(R.string.password)
        val totp = stringResource(R.string.totp)
        val username = stringResource(R.string.login_identifier)
        val domains = stringResource(R.string.domains)
        val note = stringResource(R.string.note)

        var isPasswordHidden by rememberSaveable { mutableStateOf(true) }

        val progress = remember { Animatable(1f) }
        val totpInformation = state.totpInformation
        LaunchedEffect(totpInformation.validUntil, totpInformation.code) {
            val remaining = totpInformation.validUntil - System.currentTimeMillis()

            progress.snapTo(remaining / totpInformation.maxLifetime.toFloat())
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = remaining.toInt(),
                    easing = LinearEasing
                )
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .padding(start = 8.dp, end = 8.dp, top = 8.dp)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            entry(
                title = name,
                leadingIcon = Icons.Default.Badge
            ) {
                Text(text = state.name)
            }
            entry(
                title = password,
                leadingIcon = Icons.Default.Password,
                modifier = Modifier.pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        isPasswordHidden = false

                        val pointerId = down.id
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId }
                            if (change == null || change.changedToUpIgnoreConsumed()) {
                                break
                            }

                            change.consume()
                        } while (true)

                        isPasswordHidden = true
                    }
                },
                trailingContent = {
                    CopyToClipboardButton(state.password.raw)
                }
            ) {
                val scrollState = rememberScrollState()
                Text(
                    text = if (isPasswordHidden) state.password.hidden else state.password.raw,
                    maxLines = 1,
                    modifier = Modifier.horizontalScroll(scrollState)
                )
                StrengthIndicator(
                    score = state.passwordStrengthScore,
                    forceCompact = true
                )
            }

            if (state.totpInformation.code.isNotBlank()) {
                entry(
                    title = totp,
                    leadingIcon = Icons.Default.AccessTime,
                    trailingContent = {
                        CopyToClipboardButton(state.totpInformation.code)
                    }
                ) {
                    Text(text = state.totpInformation.code.chunked(3).joinToString(" "))
                    LinearProgressIndicator(
                        progress = { progress.value },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (state.username.isNotBlank()) {
                entry(
                    title = username,
                    leadingIcon = Icons.Default.Person
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
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.domains.forEach {
                            key(it.value) {
                                AssistChip(
                                    onClick = {
                                        onEvent(ViewPasswordUiEvent.OpenWebsite(it.value))
                                    },
                                    label = { Text(text = it.value) }
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
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (state.totpInformation.code.isBlank()) {
                        AddChip(
                            fieldType = FieldType.Totp,
                            onClick = { onEvent(ViewPasswordUiEvent.OnModifyFieldRequest(it)) }
                        )
                    }
                    if (state.username.isBlank()) {
                        AddChip(
                            fieldType = FieldType.Username,
                            onClick = { onEvent(ViewPasswordUiEvent.OnModifyFieldRequest(it)) }
                        )
                    }

                    AddChip(
                        fieldType = FieldType.Domain,
                        onClick = { onEvent(ViewPasswordUiEvent.OnModifyFieldRequest(it)) }
                    )

                    if (state.note.isBlank()) {
                        AddChip(
                            fieldType = FieldType.Note,
                            onClick = { onEvent(ViewPasswordUiEvent.OnModifyFieldRequest(it)) }
                        )
                    }
                }
            }
        }

        state.modificationDialog?.let { dialog ->
            AlertDialog(
                onDismissRequest = { onEvent(ViewPasswordUiEvent.OnCloseDialog) },
                confirmButton = {
                    TextButton(
                        onClick = { onEvent(ViewPasswordUiEvent.OnSubmitModification) }
                    ) {
                        Text(text = stringResource(R.string.add))
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
                    Text(text = stringResource(R.string.add))
                },
                text = {
                    KeyGoFormField(
                        state = dialog.textFieldState,
                        label = {
                            Text(text = dialog.fieldType.addLabel())
                        },
                        modifier = Modifier.fillMaxWidth(),
                        isSecure = dialog.fieldType.isSensitive,
                        inputTransformation = if (!dialog.fieldType.isSensitive)
                            TrimTransformation
                        else null,
                    )
                },
            )
        }
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
                contentDescription = null
            )
        }
    )
}

@Composable
private fun FieldType.addLabel(): String {
    return when (this) {
        FieldType.Name -> stringResource(R.string.name)
        FieldType.Password -> stringResource(R.string.password)
        FieldType.Totp -> stringResource(R.string.add_totp)
        FieldType.Username -> stringResource(R.string.add_username)
        FieldType.Domain -> stringResource(R.string.add_domain)
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
        FieldType.Note -> Icons.AutoMirrored.Default.NoteAdd
    }
}

private fun LazyListScope.entry(
    title: String,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    trailingContent: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    item(key = title) {
        KeyGoCard(
            title = {
                Text(text = title)
            },
            leadingItem = {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                )
            },
            trailingItem = trailingContent,
            modifier = modifier.animateItem()
        ) {
            content()
        }
    }
}

@Preview
@Composable
private fun ViewPasswordContentPreview() {
    MaterialTheme {
        CompositionLocalProvider(
            LocalIsInSinglePaneMode provides true
        ) {
            ViewPasswordContent(
                state = ViewPasswordState(
                    name = "Password 1",
                    password = ObfuscatedString("Password"),
                    passwordStrengthScore = Password.Score.Ridiculous,
                    totpInformation = TotpInformation(
                        code = "123456",
                        validUntil = System.currentTimeMillis() + 30_000L,
                        maxLifetime = 30_000L
                    ),
                    username = "Username 1",
                    domains = setOf(
                        DomainInfo(
                            passwordId = 1,
                            value = "login.example.com",
                            eTLD1 = "example.com"
                        )
                    ),
                    note = "Note about the password or any additional information that might be useful.",
                ),
                onEvent = {}
            )
        }
    }
}

@Preview
@Composable
private fun ViewPasswordContentModificationDialogPreview() {
    MaterialTheme {
        CompositionLocalProvider(
            LocalIsInSinglePaneMode provides true
        ) {
            ViewPasswordContent(
                state = ViewPasswordState(
                    name = "Password 1",
                    password = ObfuscatedString("Password"),
                    passwordStrengthScore = Password.Score.Ridiculous,
                    username = "Username 1",
                    domains = setOf(
                        DomainInfo(
                            passwordId = 1,
                            value = "login.example.com",
                            eTLD1 = "example.com"
                        )
                    ),
                    modificationDialog = ModificationDialog(
                        fieldType = FieldType.Name,
                        textFieldState = rememberTextFieldState()
                    )
                ),
                onEvent = {}
            )
        }
    }
}