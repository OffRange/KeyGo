package de.davis.keygo.item.core.presentation.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material3.InputChip
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldLabelPosition
import androidx.compose.material3.TextFieldLabelScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.davis.keygo.core.presentation.component.minimizedLabelHalfHeight
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <I> ChipTextField(
    items: List<I>,
    onNewItem: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    label: @Composable (TextFieldLabelScope.() -> Unit)? = null,
    labelPosition: TextFieldLabelPosition = TextFieldLabelPosition.Attached(),
    textStyle: TextStyle = LocalTextStyle.current,
    outputTransformation: OutputTransformation? = null,
    contentPadding: PaddingValues = OutlinedTextFieldDefaults.contentPadding(),
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    chipBuilder: @Composable (I) -> Unit
) {
    val state = rememberTextFieldState()
    val interactionSource = remember { MutableInteractionSource() }
    val lineLimits = TextFieldLineLimits.SingleLine
    val labelPosition = when (labelPosition) {
        is TextFieldLabelPosition.Attached -> TextFieldLabelPosition.Attached(
            alwaysMinimize = items.isNotEmpty()
        )

        else -> labelPosition
    }

    val textColor = textStyle.color.takeOrElse {
        val focused = interactionSource.collectIsFocusedAsState().value
        with(colors) {
            when {
                !enabled -> disabledTextColor
                isError -> errorTextColor
                focused -> focusedTextColor
                else -> unfocusedTextColor
            }
        }
    }
    val mergedTextStyle = textStyle.merge(TextStyle(color = textColor))

    CompositionLocalProvider(
        LocalTextSelectionColors provides colors.textSelectionColors
    ) {
        BasicTextField(
            state = state,
            modifier = modifier
                .then(
                    if (label != null && labelPosition !is TextFieldLabelPosition.Above) {
                        Modifier
                            // Merge semantics at the beginning of the modifier chain to ensure
                            // padding is considered part of the text field.
                            .semantics(mergeDescendants = true) {}
                            .padding(top = minimizedLabelHalfHeight())
                    } else {
                        Modifier
                    }
                )
                .defaultMinSize(
                    minWidth = OutlinedTextFieldDefaults.MinWidth,
                    minHeight = OutlinedTextFieldDefaults.MinHeight,
                )
                .onPreviewKeyEvent {
                    if (it.key == Key.Comma) {
                        if (state.text.removePrefix(",").isNotEmpty()) {
                            onNewItem(state.text.toString())
                        }
                        state.clearText()
                        true
                    } else false
                },
            textStyle = mergedTextStyle,
            cursorBrush = SolidColor(if (isError) colors.errorCursorColor else colors.cursorColor),
            interactionSource = interactionSource,
            outputTransformation = outputTransformation,
            lineLimits = lineLimits,
            decorator = { inner ->
                OutlinedTextFieldDefaults.decorator(
                    state = state,
                    enabled = true,
                    lineLimits = lineLimits,
                    outputTransformation = outputTransformation,
                    interactionSource = interactionSource,
                    label = label,
                    labelPosition = labelPosition,
                    contentPadding = contentPadding,
                    colors = colors,
                ).Decoration {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(interactionSource = interactionSource, null) {},
                        verticalArrangement = Arrangement.Center,
                        itemVerticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items.forEach {
                            chipBuilder(it)
                        }

                        Box(
                            modifier = Modifier.weight(1f, fill = false),
                        ) {
                            inner()
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun <I> ChipTextField2(
    items: List<I>,
    onNewItem: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    label: @Composable (TextFieldLabelScope.() -> Unit)? = null,
    labelPosition: TextFieldLabelPosition = TextFieldLabelPosition.Attached(),
    textStyle: TextStyle = LocalTextStyle.current,
    outputTransformation: OutputTransformation? = null,
    contentPadding: PaddingValues = OutlinedTextFieldDefaults.contentPadding(),
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    chipBuilder: @Composable (I) -> Unit,

    // === New ergonomic params (all have sensible defaults) ===
    delimiters: Set<Char> = setOf(',', ';', '\n'),
    commitOnImeAction: Boolean = true,
    imeAction: ImeAction = ImeAction.Done,
    placeholder: (@Composable () -> Unit)? = null,
    chipSpacing: Dp = 8.dp,
    normalize: (String) -> String = { it.trim().trimStart(*delimiters.toCharArray()) },
    allowDuplicates: Boolean = true,
    isDuplicate: (existing: I, token: String) -> Boolean = { _, _ -> false },
    onRemoveLastItem: (() -> Unit)? = null,
) {
    val state = rememberTextFieldState()
    val interactionSource = remember { MutableInteractionSource() }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    // Keep the label minimized if we have chips.
    val computedLabelPosition = when (labelPosition) {
        is TextFieldLabelPosition.Attached ->
            TextFieldLabelPosition.Attached(alwaysMinimize = items.isNotEmpty())

        else -> labelPosition
    }

    // Resolve text color from the provided colors the same way you did.
    val focused by interactionSource.collectIsFocusedAsState()
    val baseTextColor = textStyle.color.takeOrElse {
        with(colors) {
            when {
                !enabled -> disabledTextColor
                isError -> errorTextColor
                focused -> focusedTextColor
                else -> unfocusedTextColor
            }
        }
    }
    val mergedTextStyle = textStyle.merge(TextStyle(color = baseTextColor))

    // --- Helpers -------------------------------------------------------------------------------

    fun commitToken(raw: String) {
        val token = normalize(raw)
        if (token.isEmpty()) return
        val isDup = !allowDuplicates && items.any { isDuplicate(it, token) }
        if (!isDup) onNewItem(token)
    }

    fun commitAllFrom(input: String): String {
        // Split by delimiters but keep the trailing partial (if any).
        val acc = StringBuilder()
        var partial = StringBuilder()
        for (c in input) {
            if (c in delimiters) {
                commitToken(partial.toString())
                partial = StringBuilder()
            } else {
                partial.append(c)
            }
        }
        // Return the leftover partial (not yet committed).
        return partial.toString()
    }

    fun commitCurrentAndClear() {
        val leftover = normalize(state.text.toString())
        if (leftover.isNotEmpty()) {
            commitToken(leftover)
            state.clearText()
        }
    }

    // Observe the text as it changes and consume tokens whenever a delimiter appears.
    LaunchedEffect(state, items, delimiters, allowDuplicates) {
        snapshotFlow { state.text.toString() }
            .collect { text ->
                // Fast check: skip if no delimiter present.
                if (text.any { it in delimiters }) {
                    val remaining = commitAllFrom(text)
                    if (remaining != text) {
                        state.edit {
                            delete(0, length)
                            append(remaining)
                        }
                    }
                }
            }
    }

    // Keep caret visible as chips wrap.
    LaunchedEffect(focused) {
        if (focused) {
            // A tiny delay lets layout settle before requesting visibility.
            scope.launch {
                //delay(10)
                awaitFrame()
                bringIntoViewRequester.bringIntoView()
            }
        }
    }

    CompositionLocalProvider(
        LocalTextSelectionColors provides colors.textSelectionColors
    ) {
        BasicTextField(
            state = state,
            enabled = enabled,
            textStyle = mergedTextStyle,
            cursorBrush = SolidColor(if (isError) colors.errorCursorColor else colors.cursorColor),
            interactionSource = interactionSource,
            outputTransformation = outputTransformation,
            lineLimits = TextFieldLineLimits.SingleLine,
            keyboardOptions = KeyboardOptions(imeAction = imeAction),
            onKeyboardAction = {
                commitCurrentAndClear()
            },
            modifier = modifier
                // Merge semantics early so padding is included.
                .then(
                    if (label != null && computedLabelPosition !is TextFieldLabelPosition.Above) {
                        Modifier
                            .semantics(mergeDescendants = true) {}
                            .padding(top = minimizedLabelHalfHeight())
                    } else {
                        Modifier
                    }
                )
                .defaultMinSize(
                    minWidth = OutlinedTextFieldDefaults.MinWidth,
                    minHeight = OutlinedTextFieldDefaults.MinHeight,
                )
                .focusRequester(focusRequester)
                .onFocusChanged {
                    // Commit whatever is typed when the user leaves the field.
                    if (!it.isFocused) commitCurrentAndClear()
                }
                .onPreviewKeyEvent { event ->
                    when {
                        // Hardware comma / semicolon / enter still supported as a convenience.
                        event.key == Key.Comma || event.key == Key.Semicolon -> {
                            commitCurrentAndClear()
                            true
                        }
                        // Backspace removes last chip when the field is empty.
                        event.key == Key.Backspace && state.text.isEmpty() && onRemoveLastItem != null -> {
                            onRemoveLastItem()
                            true
                        }

                        else -> false
                    }
                },
            decorator = { inner ->
                OutlinedTextFieldDefaults
                    .decorator(
                        state = state,
                        enabled = enabled,
                        isError = isError,
                        lineLimits = TextFieldLineLimits.SingleLine,
                        outputTransformation = outputTransformation,
                        interactionSource = interactionSource,
                        label = label,
                        labelPosition = computedLabelPosition,
                        contentPadding = contentPadding,
                        colors = colors,
                    )
                    .Decoration {
                        // Click anywhere in the container to focus the text field.
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clipToBounds()
                                .bringIntoViewRequester(bringIntoViewRequester)
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null
                                ) { focusRequester.requestFocus() },
                            verticalArrangement = Arrangement.Center,
                            itemVerticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(chipSpacing)
                        ) {
                            items.forEach { chipBuilder(it) }

                            // Placeholder when empty.
                            if (items.isEmpty() && state.text.isEmpty() && enabled) {
                                if (placeholder != null) {
                                    Box(
                                        modifier = Modifier
                                            .padding(end = 2.dp) // tiny gap before caret
                                    ) { placeholder() }
                                }
                            }

                            // The input itself takes the remaining line space.
                            Box(modifier = Modifier.weight(1f, fill = false)) {
                                inner()
                            }
                        }
                    }
            }
        )
    }
}

@Preview
@Composable
private fun ChipTextFieldEmptyPreview() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxWidth()) {
            val items = remember {
                mutableStateListOf<String>()
            }
            ChipTextField2(
                items = items,
                onNewItem = {
                    items.add(it)
                },
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                label = {
                    Text(text = "Domains")
                },
                chipBuilder = {
                    InputChip(
                        selected = false,
                        onClick = {},
                        label = { Text(it) },
                    )
                }
            )
        }
    }
}

@Preview
@Composable
private fun ChipTextFieldWithItemsPreview() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxWidth()) {
            val items = remember {
                mutableStateListOf("example.com", "example.org")
            }
            ChipTextField(
                items = items,
                onNewItem = {
                    items.add(it)
                },
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                label = {
                    Text(text = "Domains")
                }
            ) {
                InputChip(
                    selected = false,
                    onClick = {},
                    label = { Text(it) },
                )
            }
        }
    }
}