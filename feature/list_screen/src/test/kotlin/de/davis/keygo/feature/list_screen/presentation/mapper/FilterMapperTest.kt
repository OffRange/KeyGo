package de.davis.keygo.feature.list_screen.presentation.mapper

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.PasswordScore
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
    private val noTags: Map<ItemId, Set<String>> = emptyMap()

    @Test
    fun `labels include only tags carried by a visible item, in allTags order`() {
        val a = TestLiteItem("A")
        val b = TestLiteItem("B")

        val options = listOf(a, b).toAvailableFilterOptions(
            passwordScores = noScores,
            tagsByItem = mapOf(
                a.id to setOf("Bank"),
                b.id to setOf("Work"),
            ),
            // "Personal" belongs to no visible item -> must be excluded.
            allTags = listOf("Bank", "Personal", "Work"),
        )

        // Order follows the (already sorted) allTags list.
        assertEquals(listOf("Bank", "Work"), options.labels.toList())
    }

    @Test
    fun `tags belonging only to items absent from the list are excluded`() {
        val a = TestLiteItem("A")
        val hidden = TestLiteItem("Hidden")

        val options = listOf(a).toAvailableFilterOptions(
            passwordScores = noScores,
            tagsByItem = mapOf(
                a.id to setOf("Bank"),
                hidden.id to setOf("Secret"),
            ),
            allTags = listOf("Bank", "Secret"),
        )

        assertEquals(setOf("Bank"), options.labels)
    }

    @Test
    fun `no labels when no visible item has tags`() {
        val options = listOf(TestLiteItem("A")).toAvailableFilterOptions(
            passwordScores = noScores,
            tagsByItem = noTags,
            allTags = listOf("Bank"),
        )

        assertTrue(options.labels.isEmpty())
    }

    @Test
    fun `selected labels are reflected as selected chips`() {
        val a = TestLiteItem("A")
        val available = listOf(a).toAvailableFilterOptions(
            passwordScores = noScores,
            tagsByItem = mapOf(a.id to setOf("Bank", "Work")),
            allTags = listOf("Bank", "Work"),
        )

        val sheet = FilterState(selectedLabels = setOf("Bank"))
            .toBottomSheetState(available, restrictedItemType = null)

        val chips = sheet.itemSection?.labelChips.orEmpty()
        assertEquals(setOf("Bank", "Work"), chips.map { it.value }.toSet())
        assertTrue(chips.single { it.value == "Bank" }.selected)
        assertTrue(!chips.single { it.value == "Work" }.selected)
    }
}
