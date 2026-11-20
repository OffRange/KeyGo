package de.davis.keygo.feature.account.presentation.setup.transformation

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.text.TextRange
import de.davis.keygo.rust.FakeUsernameValidator
import kotlin.test.Test
import kotlin.test.assertEquals

class UsernameInputTransformationTest {

    private val transformation = UsernameInputTransformation(FakeUsernameValidator()::sanitize)

    private fun TextFieldState.type(text: String, at: Int) = edit {
        replace(at, at, text)
        selection = TextRange(at + text.length)
        with(transformation) { transformInput() }
    }

    @Test
    fun sanitizes_the_typed_text() {
        val state = TextFieldState("ad")

        state.type("A!", at = 2)

        assertEquals("ada", state.text.toString())
        assertEquals(TextRange(3), state.selection)
    }

    @Test
    fun keeps_the_cursor_behind_an_edit_in_the_middle() {
        val state = TextFieldState("adlace")

        state.type(" A", at = 2)

        assertEquals("adalace", state.text.toString())
        assertEquals(TextRange(3), state.selection)
    }

    @Test
    fun leaves_clean_input_untouched() {
        val state = TextFieldState("ada")

        state.type(".l", at = 3)

        assertEquals("ada.l", state.text.toString())
        assertEquals(TextRange(5), state.selection)
    }
}
