package de.davis.keygo.core.ui.text

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText

/**
 * Empties a field that held a secret. [clearText] alone records the clear as one more undoable
 * edit, so the old text would stay in memory and come back on Ctrl+Z.
 */
@OptIn(ExperimentalFoundationApi::class)
fun TextFieldState.clearSecret() {
    clearText()
    undoState.clearHistory()
}
