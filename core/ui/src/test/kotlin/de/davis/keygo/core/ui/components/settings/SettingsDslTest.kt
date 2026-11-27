package de.davis.keygo.core.ui.components.settings

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
        }.build().map { assertIs<SettingsBlock.SettingsSection>(it) }

        assertEquals(2, sections.size)
        assertEquals(1, sections[0].title)
        assertEquals(listOf(10, 11), sections[0].entries.map { assertIs<SettingsEntry>(it).title })
        assertEquals(2, sections[1].title)
        assertEquals(listOf(20), sections[1].entries.map { assertIs<SettingsEntry>(it).title })
    }

    @Test
    fun `custom block keeps its place among sections`() {
        val blocks = SettingsScope().apply {
            custom(key = "account") {}
            section(title = 1) {
                action(title = 10, onClick = {}, colors)
            }
        }.build()

        assertEquals("account", assertIs<SettingsBlock.SettingsCustom>(blocks[0]).key)
        assertEquals(1, assertIs<SettingsBlock.SettingsSection>(blocks[1]).title)
    }

    @Test
    fun `section without a title keeps its entries`() {
        val section = SettingsScope().apply {
            section {
                action(title = 10, onClick = {}, colors)
            }
        }.build().map { assertIs<SettingsBlock.SettingsSection>(it) }.single()

        assertNull(section.title)
        assertEquals(listOf(10), section.entries.map { assertIs<SettingsEntry>(it).title })
    }

    @Test
    fun `section key defaults to its title, else its position`() {
        val sections = SettingsScope().apply {
            custom(key = "banner") {}
            section(title = 1) {}
            section {}
            section(key = "danger") {}
        }.build().filterIsInstance<SettingsBlock.SettingsSection>()

        assertEquals(listOf("1", "section_2", "danger"), sections.map { it.key })
    }

    @Test
    fun `custom item keeps its place among a section's entries`() {
        val entries = SectionScope().apply {
            action(title = 10, onClick = {}, colors)
            custom(key = "card") {}
            action(title = 11, onClick = {}, colors)
        }.build()

        assertEquals(10, assertIs<SettingsEntry>(entries[0]).title)
        assertEquals("card", assertIs<SettingsBlock.SettingsCustom>(entries[1]).key)
        assertEquals(11, assertIs<SettingsEntry>(entries[2]).title)
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

        assertEquals(listOf(11), entries.map { assertIs<SettingsEntry>(it).title })
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

    @Test
    fun `entries without colors leave them to the list default`() {
        val entries = SectionScope().apply {
            toggle(title = 10, checked = true, onCheckedChange = {})
            action(title = 11, onClick = {})
            value(title = 12, value = "v")
            picker(title = 13, selected = "a", options = listOf("a"), label = { UIText.RawString(it) }, onSelect = {})
        }.build()

        assertTrue(entries.all { assertIs<SettingsEntry>(it).colors == null })
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
