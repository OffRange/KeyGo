package de.davis.keygo.core.ui.text.transformation

import androidx.compose.foundation.text.input.TextFieldState
import kotlin.test.Test
import kotlin.test.assertEquals

class SchemeStrippingTransformationTest {

    private val reported = mutableListOf<String?>()
    private val transformation = SchemeStrippingTransformation { reported += it }
    private val state = TextFieldState()

    private fun input(text: String) = state.edit {
        replace(0, length, text)
        with(transformation) { transformInput() }
    }

    @Test
    fun strips_and_reports_a_leading_scheme() {
        input("http://localhost:8000")

        assertEquals("localhost:8000", state.text.toString())
        assertEquals(listOf<String?>("http://"), reported)
    }

    @Test
    fun leaves_input_without_a_scheme_untouched() {
        input("vault.example.org")

        assertEquals("vault.example.org", state.text.toString())
        assertEquals(emptyList(), reported)
    }

    @Test
    fun reports_no_scheme_once_the_field_is_cleared() {
        input("https://vault.example.org")
        input("")

        assertEquals(listOf("https://", null), reported)
    }
}
