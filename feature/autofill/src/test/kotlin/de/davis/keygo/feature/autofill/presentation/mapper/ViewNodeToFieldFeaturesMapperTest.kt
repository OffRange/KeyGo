package de.davis.keygo.feature.autofill.presentation.mapper

import de.davis.keygo.feature.autofill.presentation.model.FieldFeatures
import kotlin.test.Test
import kotlin.test.assertEquals

internal class ViewNodeToFieldFeaturesMapperTest {

    @Test
    fun `null autofill hints yield empty set`() {
        val result = buildFieldFeatures(
            autofillHints = null,
            htmlAttributes = null,
            idEntry = null,
            hint = null,
            text = null,
        )
        assertEquals(emptySet(), result.autofillHints)
    }

    @Test
    fun `blank autofill hints are filtered out`() {
        val result = buildFieldFeatures(
            autofillHints = arrayOf("", "  ", "username"),
            htmlAttributes = null,
            idEntry = null,
            hint = null,
            text = null,
        )
        assertEquals(setOf("username"), result.autofillHints)
    }

    @Test
    fun `valid autofill hints are preserved`() {
        val result = buildFieldFeatures(
            autofillHints = arrayOf("username", "password"),
            htmlAttributes = null,
            idEntry = null,
            hint = null,
            text = null,
        )
        assertEquals(setOf("username", "password"), result.autofillHints)
    }

    @Test
    fun `null html attributes yield empty map`() {
        val result = buildFieldFeatures(
            autofillHints = null,
            htmlAttributes = null,
            idEntry = null,
            hint = null,
            text = null,
        )
        assertEquals(emptyMap(), result.htmlAttributes)
    }

    @Test
    fun `html attributes with blank key or value are filtered out`() {
        val result = buildFieldFeatures(
            autofillHints = null,
            htmlAttributes = listOf(
                "" to "value",
                "key" to "",
                "  " to "value",
                "type" to "  ",
                "type" to "text",
            ),
            idEntry = null,
            hint = null,
            text = null,
        )
        assertEquals(mapOf("type" to "text"), result.htmlAttributes)
    }

    @Test
    fun `valid html attribute pairs are preserved`() {
        val result = buildFieldFeatures(
            autofillHints = null,
            htmlAttributes = listOf(
                "type" to "password",
                "name" to "email",
            ),
            idEntry = null,
            hint = null,
            text = null,
        )
        assertEquals(mapOf("type" to "password", "name" to "email"), result.htmlAttributes)
    }

    @Test
    fun `null token inputs yield empty set`() {
        val result = buildFieldFeatures(
            autofillHints = null,
            htmlAttributes = null,
            idEntry = null,
            hint = null,
            text = null,
        )
        assertEquals(emptySet(), result.tokens)
    }

    @Test
    fun `blank token inputs are excluded`() {
        val result = buildFieldFeatures(
            autofillHints = null,
            htmlAttributes = null,
            idEntry = "",
            hint = "   ",
            text = "",
        )
        assertEquals(emptySet(), result.tokens)
    }

    @Test
    fun `tokens are lowercased`() {
        val result = buildFieldFeatures(
            autofillHints = null,
            htmlAttributes = null,
            idEntry = "UserName",
            hint = "Enter Email",
            text = "PASSWORD",
        )
        assertEquals(setOf("username", "enter email", "password"), result.tokens)
    }

    @Test
    fun `tokens are deduplicated`() {
        val result = buildFieldFeatures(
            autofillHints = null,
            htmlAttributes = null,
            idEntry = "email",
            hint = "EMAIL",
            text = "Email",
        )
        assertEquals(setOf("email"), result.tokens)
    }

    @Test
    fun `all null inputs return empty FieldFeatures`() {
        val result = buildFieldFeatures(
            autofillHints = null,
            htmlAttributes = null,
            idEntry = null,
            hint = null,
            text = null,
        )
        assertEquals(FieldFeatures(emptySet(), emptyMap(), emptySet()), result)
    }
}
