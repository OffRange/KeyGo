package de.davis.keygo.feature.list_screen.presentation.mapper

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.item.domain.model.Tag
import de.davis.keygo.core.item.domain.model.lite.LiteItem
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.feature.list_screen.domain.model.FilterState
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FilterMapperTest {

    private data class TestLiteItem(
        override val name: String,
        override val id: ItemId = UUID.nameUUIDFromBytes(name.toByteArray()),
        override val itemType: VaultItemType = VaultItemType.Login,
        override val pinned: Boolean = false,
    ) : LiteItem

    private val noScores: Map<ItemId, PasswordScore> = emptyMap()
    private val noTags: Map<ItemId, Set<Tag>> = emptyMap()

    private fun tag(value: String): Tag = Tag.of(value)!!

    @Test
    fun `tags include only tags carried by a visible item, in allTags order`() {
        val a = TestLiteItem("A")
        val b = TestLiteItem("B")

        val options = listOf(a, b).toAvailableFilterOptions(
            passwordScores = noScores,
            tagsByItem = mapOf(
                a.id to setOf(tag("Bank")),
                b.id to setOf(tag("Work")),
            ),
            // "Personal" belongs to no visible item -> must be excluded.
            allTags = listOf(tag("Bank"), tag("Personal"), tag("Work")),
        )

        // Order follows the (already sorted) allTags list.
        assertEquals(listOf(tag("Bank"), tag("Work")), options.tags.toList())
    }

    @Test
    fun `tags belonging only to items absent from the list are excluded`() {
        val a = TestLiteItem("A")
        val hidden = TestLiteItem("Hidden")

        val options = listOf(a).toAvailableFilterOptions(
            passwordScores = noScores,
            tagsByItem = mapOf(
                a.id to setOf(tag("Bank")),
                hidden.id to setOf(tag("Secret")),
            ),
            allTags = listOf(tag("Bank"), tag("Secret")),
        )

        assertEquals(setOf(tag("Bank")), options.tags)
    }

    @Test
    fun `no tags when no visible item has tags`() {
        val options = listOf(TestLiteItem("A")).toAvailableFilterOptions(
            passwordScores = noScores,
            tagsByItem = noTags,
            allTags = listOf(tag("Bank")),
        )

        assertTrue(options.tags.isEmpty())
    }

    @Test
    fun `selected tags are reflected as selected chips`() {
        val a = TestLiteItem("A")
        val available = listOf(a).toAvailableFilterOptions(
            passwordScores = noScores,
            tagsByItem = mapOf(a.id to setOf(tag("Bank"), tag("Work"))),
            allTags = listOf(tag("Bank"), tag("Work")),
        )

        val sheet = FilterState(selectedTags = setOf(tag("Bank")))
            .toBottomSheetState(available, restrictedItemType = null)

        val chips = sheet.itemSection?.tagChips.orEmpty()
        assertEquals(setOf(tag("Bank"), tag("Work")), chips.map { it.value }.toSet())
        assertTrue(chips.single { it.value == tag("Bank") }.selected)
        assertTrue(!chips.single { it.value == tag("Work") }.selected)
    }
}
