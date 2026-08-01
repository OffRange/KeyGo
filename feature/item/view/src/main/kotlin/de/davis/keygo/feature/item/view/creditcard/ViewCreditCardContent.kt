package de.davis.keygo.feature.item.view.creditcard

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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.davis.keygo.core.item.presentation.toImageVector
import de.davis.keygo.core.ui.components.KeyGoCard
import de.davis.keygo.core.ui.composition.LocalIsInSinglePaneMode
import de.davis.keygo.feature.item.core.presentation.component.CopyToClipboardButton
import de.davis.keygo.feature.item.core.presentation.component.KeyGoFormField
import de.davis.keygo.feature.item.core.presentation.component.KeyGoFormSuggestionField
import de.davis.keygo.feature.item.core.presentation.transformation.TrimTransformation
import de.davis.keygo.feature.item.view.R
import de.davis.keygo.feature.item.view.creditcard.model.CreditCardFieldType
import de.davis.keygo.feature.item.view.creditcard.model.ViewCreditCardState
import de.davis.keygo.feature.item.view.creditcard.model.ViewCreditCardUiEvent
import de.davis.keygo.feature.item.view.login.model.ObfuscatedString
import de.davis.keygo.feature.item.view.onHold
import de.davis.keygo.core.item.R as CoreItemR
import de.davis.keygo.core.ui.R as CoreUiR
import de.davis.keygo.feature.item.core.R as ItemCoreR

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ViewCreditCardContent(state: ViewCreditCardState, onEvent: (ViewCreditCardUiEvent) -> Unit) {
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
                            Text(text = stringResource(CoreItemR.string.credit_card))
                        }
                    }
                },
                navigationIcon = {
                    if (LocalIsInSinglePaneMode.current) {
                        IconButton(onClick = { onEvent(ViewCreditCardUiEvent.OnBackClick) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Default.ArrowBack,
                                contentDescription = stringResource(ItemCoreR.string.back_content_description),
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onEvent(ViewCreditCardUiEvent.OnPinClick) },
                    ) {
                        Icon(
                            imageVector = if (state.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = null,
                        )
                    }

                    IconButton(
                        onClick = { onEvent(ViewCreditCardUiEvent.OnEditRequest) },
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
        val cardholder = stringResource(ItemCoreR.string.cc_holder)
        val cardNumber = stringResource(ItemCoreR.string.cc_number)
        val cvv = stringResource(ItemCoreR.string.cc_cvv)
        val expiration = stringResource(ItemCoreR.string.cc_expiration_date)
        val tags = stringResource(ItemCoreR.string.tags)
        val note = stringResource(ItemCoreR.string.note)

        var isCardNumberHidden by rememberSaveable { mutableStateOf(true) }
        var isCvvHidden by rememberSaveable { mutableStateOf(true) }

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

            if (state.holder.isNotBlank()) {
                entry(
                    title = cardholder,
                    leadingIcon = Icons.Default.Person,
                ) {
                    Text(text = state.holder)
                }
            }

            val cardNum = state.cardNumber
            if (cardNum != null) {
                entry(
                    title = cardNumber,
                    leadingIcon = Icons.Default.CreditCard,
                    modifier = Modifier.onHold {
                        isCardNumberHidden = !it
                    },
                    trailingContent = {
                        CopyToClipboardButton(cardNum.raw)
                    },
                ) {
                    val scrollState = rememberScrollState()
                    Text(
                        text = if (isCardNumberHidden) cardNum.hidden else cardNum.formatted,
                        maxLines = 1,
                        modifier = Modifier.horizontalScroll(scrollState),
                    )
                }
            }

            val cvvVal = state.cvv
            if (cvvVal != null) {
                entry(
                    title = cvv,
                    leadingIcon = Icons.Default.Pin,
                    modifier = Modifier.onHold {
                        isCvvHidden = !it
                    },
                    trailingContent = {
                        CopyToClipboardButton(cvvVal.raw)
                    },
                ) {
                    val scrollState = rememberScrollState()
                    Text(
                        text = if (isCvvHidden) cvvVal.hidden else cvvVal.raw,
                        maxLines = 1,
                        modifier = Modifier.horizontalScroll(scrollState),
                    )
                }
            }

            if (state.expirationDate.isNotBlank()) {
                entry(
                    title = expiration,
                    leadingIcon = Icons.Default.CalendarMonth,
                ) {
                    Text(text = state.expirationDate)
                }
            }

            if (state.tags.isNotEmpty()) {
                entry(
                    title = tags,
                    leadingIcon = Icons.Default.Sell,
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
                    if (state.holder.isBlank()) {
                        AddChip(
                            fieldType = CreditCardFieldType.Holder,
                            onClick = { onEvent(ViewCreditCardUiEvent.OnModifyFieldRequest(it)) },
                        )
                    }

                    if (state.cardNumber == null) {
                        AddChip(
                            fieldType = CreditCardFieldType.CardNumber,
                            onClick = { onEvent(ViewCreditCardUiEvent.OnModifyFieldRequest(it)) },
                        )
                    }

                    if (state.cvv == null) {
                        AddChip(
                            fieldType = CreditCardFieldType.Cvv,
                            onClick = { onEvent(ViewCreditCardUiEvent.OnModifyFieldRequest(it)) },
                        )
                    }

                    if (state.expirationDate.isBlank()) {
                        AddChip(
                            fieldType = CreditCardFieldType.Expiration,
                            onClick = { onEvent(ViewCreditCardUiEvent.OnModifyFieldRequest(it)) },
                        )
                    }

                    AddChip(
                        fieldType = CreditCardFieldType.Tag,
                        onClick = { onEvent(ViewCreditCardUiEvent.OnModifyFieldRequest(it)) },
                    )

                    if (state.note.isBlank()) {
                        AddChip(
                            fieldType = CreditCardFieldType.Note,
                            onClick = { onEvent(ViewCreditCardUiEvent.OnModifyFieldRequest(it)) },
                        )
                    }
                }
            }
        }

        state.modificationDialog?.let { dialog ->
            val textFieldInputState = rememberTextFieldState(dialog.initialValue)
            AlertDialog(
                onDismissRequest = { onEvent(ViewCreditCardUiEvent.OnCloseDialog) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onEvent(
                                ViewCreditCardUiEvent.OnSubmitModification(
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
                        CreditCardFieldType.Tag -> KeyGoFormSuggestionField(
                            suggestions = dialog.tagsToSuggest.mapTo(mutableSetOf()) { it.display },
                            onSuggestionSelected = {
                                textFieldInputState.setTextAndPlaceCursorAtEnd(it)
                            },
                            state = textFieldInputState,
                            label = {
                                Text(text = dialog.fieldType.addLabel())
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        else -> KeyGoFormField(
                            state = textFieldInputState,
                            label = {
                                Text(text = dialog.fieldType.addLabel())
                            },
                            modifier = Modifier.fillMaxWidth(),
                            isSecure = dialog.fieldType.isSensitive,
                            inputTransformation = if (!dialog.fieldType.isSensitive) TrimTransformation else null,
                            error = dialog.error,
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun AddChip(fieldType: CreditCardFieldType, onClick: (CreditCardFieldType) -> Unit) {
    AssistChip(
        onClick = { onClick(fieldType) },
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
private fun CreditCardFieldType.addLabel(): String {
    return when (this) {
        CreditCardFieldType.Holder -> stringResource(R.string.add_holder)
        CreditCardFieldType.CardNumber -> stringResource(R.string.add_card_number)
        CreditCardFieldType.Cvv -> stringResource(R.string.add_cvv)
        CreditCardFieldType.Expiration -> stringResource(R.string.add_expiration)
        CreditCardFieldType.Tag -> stringResource(R.string.add_tag)
        CreditCardFieldType.Note -> stringResource(R.string.add_note)
    }
}

@Composable
private fun CreditCardFieldType.addIcon(): ImageVector {
    return when (this) {
        CreditCardFieldType.Holder -> Icons.Default.PersonAdd
        CreditCardFieldType.CardNumber -> Icons.Default.CreditCard
        CreditCardFieldType.Cvv -> Icons.Default.Pin
        CreditCardFieldType.Expiration -> Icons.Default.CalendarMonth
        CreditCardFieldType.Tag -> Icons.Default.Sell
        CreditCardFieldType.Note -> Icons.AutoMirrored.Default.NoteAdd
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
            modifier = modifier.animateItem(),
        ) {
            content()
        }
    }
}

@Preview
@Composable
private fun ViewCreditCardContentPreview() {
    MaterialTheme {
        CompositionLocalProvider(
            LocalIsInSinglePaneMode provides true
        ) {
            ViewCreditCardContent(
                state = ViewCreditCardState(
                    name = "My Visa",
                    holder = "John Doe",
                    cardNumber = ObfuscatedString(
                        "4111111111111111",
                        formatted = "4111 1111 1111 1111",
                        visibleSuffixDigits = 4,
                        preservedChars = setOf(' '),
                    ),
                    cvv = ObfuscatedString("123"),
                    expirationDate = "12/26",
                    note = "Main travel card.",
                ),
                onEvent = {},
            )
        }
    }
}
