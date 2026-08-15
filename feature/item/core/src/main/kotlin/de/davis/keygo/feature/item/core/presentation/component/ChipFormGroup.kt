package de.davis.keygo.feature.item.core.presentation.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.text.input.then
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldLabelScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest

@Stable
sealed interface ChipFormMode<T> {

    val suggestions: Set<T>

    class Simple<T> : ChipFormMode<T> {
        override val suggestions: Set<T> = emptySet()
    }

    data class WithSuggestions<T>(override val suggestions: Set<T>) : ChipFormMode<T>
}

@Composable
fun <T> ChipFormGroup(
    title: String,
    items: Set<T>,
    textOf: (T) -> String,
    containsForInput: (String) -> Boolean,
    onSubmit: (Set<String>) -> Unit,
    onDelete: (T) -> Unit,
    modifier: Modifier = Modifier,
    state: TextFieldState = rememberTextFieldState(),
    mode: ChipFormMode<T> = ChipFormMode.Simple(),
    onEdit: ((T, Set<String>) -> Unit)? = null,
    label: @Composable (TextFieldLabelScope.() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.SingleLine,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
    delimiters: Set<Char> = setOf(',', ' '),
    inputTransformation: InputTransformation? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val currentOnSubmit by rememberUpdatedState(onSubmit)
    val currentContainsForInput by rememberUpdatedState(containsForInput)
    val currentOnEdit by rememberUpdatedState(onEdit)

    var editingText by rememberSaveable { mutableStateOf<String?>(null) }
    val editingItem = remember(items, editingText) {
        editingText?.let { text -> items.find { textOf(it) == text } }
    }

    fun cancelEdit() {
        state.setTextAndPlaceCursorAtEnd("")
        editingText = null
    }

    fun startEdit(item: T) {
        val text = textOf(item)
        editingText = text
        state.setTextAndPlaceCursorAtEnd(text)
    }

    /** Splits [text] on [delimiters] and submits completed items via [onSubmit].
     *
     * @param keepLast If set to **true (default)**, the segment after the last delimiter stays in the field
     * (used during typing so the in-progress token isn't submitted prematurely).
     * If set to **false**, submits everything (used on focus loss and keyboard action).
     */
    fun handleText(text: String, keepLast: Boolean = true) {
        if (keepLast && text.none { it in delimiters })
            return

        val items = text.splitChipInput(delimiters)

        val (itemsToSubmit, keep) = if (!keepLast || text.last() in delimiters) items to ""
        else items.dropLast(1) to items.last()

        val submissionSet = itemsToSubmit
            .filterNot { currentContainsForInput(it) }
            .toSet()
        if (submissionSet.isNotEmpty()) {
            editingItem?.let {
                currentOnEdit?.invoke(it, submissionSet)
                editingText = null
            } ?: currentOnSubmit(submissionSet)
        }

        state.setTextAndPlaceCursorAtEnd(keep)
    }

    // Submit any remaining text when the field loses focus, or cancel editing.
    val focused by interactionSource.collectIsFocusedAsState()
    LaunchedEffect(focused) {
        if (focused) return@LaunchedEffect

        if (editingText != null) cancelEdit()
        else handleText(state.text.toString(), keepLast = false)
    }

    // React to text changes as the user types (delimiter-based splitting).
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }.collectLatest { text ->
            handleText(text)
        }
    }

    val noLeadingDelimiters = remember(delimiters) {
        InputTransformation {
            var i = 0
            while (i < length && charAt(i) in delimiters) i++
            if (i > 0) delete(0, i)
        }
    }

    val itemsToShow = remember(items, editingItem) {
        if (editingItem == null) items
        else items.filter { it != editingItem }
    }

    FormGroup(
        title = title
    ) {
        AnimatedContent(
            targetState = items.isNotEmpty(),
            transitionSpec = { (fadeIn() + expandVertically()) togetherWith (fadeOut() + shrinkVertically()) }
        ) { hasItems ->
            if (!hasItems) return@AnimatedContent
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsToShow.forEach { item ->
                    key(item.hashCode()) {
                        MenuChip(
                            chipText = textOf(item),
                            onDeleteClick = { onDelete(item) },
                            onModifyClick = { startEdit(item) },
                        )
                    }
                }
            }
        }

        val combinedTransformation = remember(inputTransformation, noLeadingDelimiters) {
            inputTransformation?.then(noLeadingDelimiters) ?: noLeadingDelimiters
        }

        // Dynamically switch IME action: Send to submit text as chips, Next to move focus.
        val hasText by remember { derivedStateOf { state.text.isNotBlank() } }
        val effectiveKeyboardOptions = remember(keyboardOptions, hasText) {
            keyboardOptions.copy(
                imeAction = if (hasText) ImeAction.Send else ImeAction.Next
            )
        }

        val modifier = modifier
            .fillMaxWidth()
            .onPreviewKeyEvent {
                if (it.key != Key.Backspace) return@onPreviewKeyEvent false
                if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                // Only trigger "un-chip" logic if the field is empty and there are items to remove
                if (state.text.isNotBlank() || items.isEmpty()) return@onPreviewKeyEvent false

                // Cancel editing if in edit mode
                if (editingItem != null) {
                    cancelEdit()
                    return@onPreviewKeyEvent true
                }

                // Pop the last item and restore its text representation to the field
                val lastItem = items.last()
                val text = textOf(lastItem)
                onDelete(lastItem)
                state.setTextAndPlaceCursorAtEnd(text)
                true
            }

        // Flush pending text as chips before forwarding the keyboard action.
        val keyboardActionHandler = KeyboardActionHandler { defaultAction ->
            if (hasText) handleText(state.text.toString(), keepLast = false)

            onKeyboardAction?.onKeyboardAction(defaultAction) ?: defaultAction()
        }

        when (mode) {
            is ChipFormMode.Simple -> KeyGoFormField(
                state = state,
                label = label,
                modifier = modifier,
                prefix = prefix,
                placeholder = placeholder,
                lineLimits = lineLimits,
                keyboardOptions = effectiveKeyboardOptions,
                onKeyboardAction = keyboardActionHandler,
                inputTransformation = combinedTransformation,
                interactionSource = interactionSource
            )

            is ChipFormMode.WithSuggestions -> KeyGoFormSuggestionField(
                suggestions = mode.suggestions.map(textOf).toSet(),
                state = state,
                onSuggestionSelected = { handleText(it, keepLast = false) },
                label = label,
                modifier = modifier,
                prefix = prefix,
                placeholder = placeholder,
                lineLimits = lineLimits,
                keyboardOptions = effectiveKeyboardOptions,
                onKeyboardAction = keyboardActionHandler,
                inputTransformation = combinedTransformation,
                interactionSource = interactionSource,
            )
        }
    }
}

private fun CharSequence.splitChipInput(delimiters: Set<Char>): List<String> =
    split(delimiters = delimiters.toCharArray())
        .mapNotNull { it.trim().takeIf(String::isNotBlank) }

/**
 * Splits [TextFieldState.text] on each character in [delimiters], trims each segment,
 * and filters out blank entries. Invokes [block] with the resulting set only when at
 * least one non-blank item is found; does nothing if the text produces no valid items.
 *
 * @param delimiters Characters used as split points.
 * @param block Receives the set of trimmed, non-blank items; only called when non-empty.
 */
fun TextFieldState.gatherPendingItems(delimiters: Set<Char>, block: (Set<String>) -> Unit) {
    val pending = text
        .splitChipInput(delimiters)
        .toSet()

    if (pending.isNotEmpty()) block(pending)
}

@Preview
@Composable
private fun ChipFormGroupPreview() {
    val items = remember {
        mutableStateSetOf<String>()
    }
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxWidth()) {
            ChipFormGroup(
                title = "Chips",
                items = items,
                textOf = { it },
                containsForInput = {
                    it in items
                },
                onSubmit = {
                    items += it
                },
                onDelete = {
                    items -= it
                },
                label = {
                    Text("Add some chips")
                },
                prefix = {
                    Text("https://")
                },
                placeholder = {
                    Text("example.com")
                }
            )
        }
    }
}
