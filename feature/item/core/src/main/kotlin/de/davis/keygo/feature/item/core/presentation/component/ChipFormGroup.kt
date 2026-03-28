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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldLabelScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter

@Composable
fun <T> ChipFormGroup(
    title: String,
    items: Set<T>,
    containsForInput: (String) -> Boolean,
    onSubmit: (Set<String>) -> Unit,
    onDelete: (T) -> Unit,
    modifier: Modifier = Modifier,
    state: TextFieldState = rememberTextFieldState(),
    label: @Composable (TextFieldLabelScope.() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.SingleLine,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
    delimiters: Set<Char> = setOf(',', ' '),
    inputTransformation: InputTransformation? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    chipBuilder: @Composable (T, Boolean) -> Unit
) {
    var lastSelected by rememberSaveable { mutableStateOf(false) }

    val currentOnSubmit by rememberUpdatedState(onSubmit)
    val currentContainsForInput by rememberUpdatedState(containsForInput)

    fun handleText(text: String, keepLast: Boolean = true) {
        lastSelected = false
        if (keepLast && text.none { it in delimiters })
            return

        val items = text.split(*delimiters.toCharArray())
            .filter { it.isNotBlank() }

        val (itemsToSubmit, keep) = if (!keepLast || text.last() in delimiters) items to ""
        else items.dropLast(1) to items.last()

        val submissionSet = itemsToSubmit
            .filterNot { currentContainsForInput(it) }
            .toSet()
        if (submissionSet.isNotEmpty()) currentOnSubmit(submissionSet)

        state.setTextAndPlaceCursorAtEnd(keep)
    }

    val focused by interactionSource.collectIsFocusedAsState()
    LaunchedEffect(Unit) {
        snapshotFlow { focused }
            .distinctUntilChanged()
            .drop(1)
            .filter { !it }
            .collectLatest {
                handleText(state.text.toString(), keepLast = false)
            }
    }


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
                items.forEachIndexed { index, item ->
                    key(item.hashCode()) {
                        chipBuilder(item, if (index == items.size - 1) lastSelected else false)
                    }
                }
            }
        }

        val combinedTransformation = remember(inputTransformation, noLeadingDelimiters) {
            inputTransformation?.then(noLeadingDelimiters) ?: noLeadingDelimiters
        }

        val hasText by remember { derivedStateOf { state.text.isNotBlank() } }
        KeyGoFormField(
            state = state,
            label = label,
            modifier = modifier
                .fillMaxWidth()
                .onPreviewKeyEvent {
                    if (state.text.isNotBlank() || items.isEmpty()) return@onPreviewKeyEvent false

                    if (it.type == KeyEventType.KeyDown && it.key == Key.Backspace) {
                        if (lastSelected) onDelete(items.last())
                        lastSelected = !lastSelected

                        true
                    } else false
                },
            prefix = prefix,
            placeholder = placeholder,
            lineLimits = lineLimits,
            keyboardOptions = keyboardOptions,
            // Flush pending text as chips before forwarding the keyboard action.
            onKeyboardAction = KeyboardActionHandler { defaultAction ->
                if (hasText)
                    handleText(state.text.toString(), keepLast = false)

                onKeyboardAction?.onKeyboardAction(defaultAction) ?: defaultAction()
            },
            inputTransformation = combinedTransformation,
            interactionSource = interactionSource
        )
    }
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
            ) { item, selected ->
                InputChip(
                    selected = selected,
                    onClick = { },
                    label = { Text(text = item) },
                    trailingIcon = if (selected) {
                        { Icon(imageVector = Icons.Default.Close, contentDescription = null) }
                    } else null
                )
            }
        }
    }
}