package de.davis.keygo.feature.settings.presentation.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SettingsDslTest {

    @Test
    fun `builder groups entries into sections in declaration order`() {
        val sections = SettingsScope().apply {
            section(title = 1) {
                toggle(title = 10, checked = true, onCheckedChange = {})
                action(title = 11, onClick = {})
            }
            section(title = 2) {
                value(title = 20, value = "2.1")
            }
        }.build()

        assertEquals(2, sections.size)
        assertEquals(1, sections[0].title)
        assertEquals(listOf(10, 11), sections[0].entries.map { it.title })
        assertEquals(2, sections[1].title)
        assertEquals(listOf(20), sections[1].entries.map { it.title })
    }

    @Test
    fun `toggle entry captures checked state and callback`() {
        var observed = false
        val entry = SectionScope().apply {
            toggle(title = 10, checked = true, onCheckedChange = { observed = it })
        }.build().single()

        val toggle = assertIs<SettingsEntry.Toggle>(entry)
        assertTrue(toggle.checked)
        toggle.onCheckedChange(true)
        assertTrue(observed)
    }

    @Test
    fun `action entry marked as navigation when requested`() {
        val entry = SectionScope().apply {
            action(title = 11, onClick = {}, isNavigation = true)
        }.build().single()

        val action = assertIs<SettingsEntry.Action>(entry)
        assertTrue(action.isNavigation)
    }

    @Test
    fun `conditional rows are excluded when condition is false`() {
        val show = false
        val entries = SectionScope().apply {
            action(title = 11, onClick = {})
            if (show) action(title = 12, onClick = {})
        }.build()

        assertEquals(listOf(11), entries.map { it.title })
    }
}
