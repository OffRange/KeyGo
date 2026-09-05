package de.davis.keygo.feature.settings.presentation.component

import androidx.compose.material3.ListItemColors
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import de.davis.keygo.core.util.presentation.UIText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsDslTest {

    @Test
    fun `builder groups entries into sections in declaration order`() {
        val sections = SettingsScope().apply {
            section(title = 1) {
                toggle(title = 10, checked = true, onCheckedChange = {}, colors)
                action(title = 11, onClick = {}, colors)
            }
            section(title = 2) {
                value(title = 20, value = "2.1", colors)
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
            toggle(
                title = 10,
                checked = true,
                onCheckedChange = { observed = it },
                colors = colors
            )
        }.build().single()

        val toggle = assertIs<SettingsEntry.Toggle>(entry)
        assertTrue(toggle.checked)
        toggle.onCheckedChange(true)
        assertTrue(observed)
    }

    @Test
    fun `action entry carries the navigation icon when provided`() {
        val icon = testIcon()
        val entry = SectionScope().apply {
            action(title = 11, onClick = {}, navigationIcon = icon, colors = colors)
        }.build().single()

        val action = assertIs<SettingsEntry.Action>(entry)
        assertEquals(icon, action.navigationIcon)
    }

    @Test
    fun `action entry has no navigation icon by default`() {
        val entry = SectionScope().apply {
            action(title = 11, onClick = {}, colors = colors)
        }.build().single()

        val action = assertIs<SettingsEntry.Action>(entry)
        assertNull(action.navigationIcon)
    }

    @Test
    fun `conditional rows are excluded when condition is false`() {
        val show = false
        val entries = SectionScope().apply {
            action(title = 11, onClick = {}, colors = colors)
            if (show) action(title = 12, onClick = {}, colors = colors)
        }.build()

        assertEquals(listOf(11), entries.map { it.title })
    }

    @Test
    fun `picker entry resolves the selected option to an index`() {
        val entry = SectionScope().apply {
            picker(
                title = 30,
                selected = "b",
                options = listOf("a", "b", "c"),
                label = { UIText.RawString(it) },
                onSelect = {},
                colors = colors,
            )
        }.build().single()

        val picker = assertIs<SettingsEntry.Picker>(entry)
        assertEquals(1, picker.selectedIndex)
        assertEquals(
            listOf("a", "b", "c").map(UIText::RawString),
            picker.options.map { it.label },
        )
    }

    @Test
    fun `picker entry supports the row with the selected label`() {
        val entry = SectionScope().apply {
            picker(
                title = 30,
                selected = "b",
                options = listOf("a", "b"),
                label = { UIText.RawString(it) },
                onSelect = {},
                colors = colors,
            )
        }.build().single()

        val picker = assertIs<SettingsEntry.Picker>(entry)
        assertEquals(UIText.RawString("b"), picker.supporting)
    }

    @Test
    fun `picker option reports the option it was built from`() {
        var observed: String? = null
        val entry = SectionScope().apply {
            picker(
                title = 30,
                selected = "a",
                options = listOf("a", "b", "c"),
                label = { UIText.RawString(it) },
                onSelect = { observed = it },
                colors = colors,
            )
        }.build().single()

        val picker = assertIs<SettingsEntry.Picker>(entry)
        picker.options[2].onSelect()

        assertEquals("c", observed)
    }

    @Test
    fun `picker entry with a selection outside its options has no supporting line`() {
        val entry = SectionScope().apply {
            picker(
                title = 30,
                selected = "z",
                options = listOf("a", "b"),
                label = { UIText.RawString(it) },
                onSelect = {},
                colors = colors,
            )
        }.build().single()

        val picker = assertIs<SettingsEntry.Picker>(entry)
        assertEquals(-1, picker.selectedIndex)
        assertNull(picker.supporting)
    }

    private fun testIcon(): ImageVector = ImageVector.Builder(
        name = "test",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).build()
}

private val colors = ListItemColors(
    contentColor = Color.Unspecified,
    leadingContentColor = Color.Unspecified,
    trailingContentColor = Color.Unspecified,
    overlineContentColor = Color.Unspecified,
    supportingContentColor = Color.Unspecified,
    disabledContainerColor = Color.Unspecified,
    disabledContentColor = Color.Unspecified,
    disabledLeadingContentColor = Color.Unspecified,
    disabledTrailingContentColor = Color.Unspecified,
    disabledOverlineContentColor = Color.Unspecified,
    disabledSupportingContentColor = Color.Unspecified,
    selectedContainerColor = Color.Unspecified,
    selectedContentColor = Color.Unspecified,
    selectedLeadingContentColor = Color.Unspecified,
    selectedTrailingContentColor = Color.Unspecified,
    selectedOverlineContentColor = Color.Unspecified,
    selectedSupportingContentColor = Color.Unspecified,
    draggedContainerColor = Color.Unspecified,
    draggedContentColor = Color.Unspecified,
    draggedLeadingContentColor = Color.Unspecified,
    draggedTrailingContentColor = Color.Unspecified,
    draggedOverlineContentColor = Color.Unspecified,
    draggedSupportingContentColor = Color.Unspecified,
    containerColor = Color.Unspecified,
)
