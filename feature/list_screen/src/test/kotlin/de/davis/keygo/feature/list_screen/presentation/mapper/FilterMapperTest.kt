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

    @Test
    fun `toAvailableFilterOptions exposes all provided tags as labels`() {
        val items = listOf(TestLiteItem("A"), TestLiteItem("B"))

        val options = items.toAvailableFilterOptions(noScores, listOf("Bank", "Work"))

        assertEquals(setOf("Bank", "Work"), options.labels)
    }

    @Test
    fun `toAvailableFilterOptions has no labels when there are no tags`() {
        val options = listOf(TestLiteItem("A")).toAvailableFilterOptions(noScores, emptyList())
        assertTrue(options.labels.isEmpty())
    }

    @Test
    fun `selected labels are reflected as selected chips`() {
        val available = listOf(TestLiteItem("A"))
            .toAvailableFilterOptions(noScores, listOf("Bank", "Work"))

        val sheet = FilterState(selectedLabels = setOf("Bank"))
            .toBottomSheetState(available, restrictedItemType = null)

        val chips = sheet.itemSection?.labelChips.orEmpty()
        assertEquals(setOf("Bank", "Work"), chips.map { it.value }.toSet())
        assertTrue(chips.single { it.value == "Bank" }.selected)
        assertTrue(!chips.single { it.value == "Work" }.selected)
    }
}
