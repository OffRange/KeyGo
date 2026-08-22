package de.davis.keygo.feature.list_screen.presentation

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.lite.LiteItem
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class SuggestedItemsTest {

    private data class TestLiteItem(
        override val name: String,
        override val id: ItemId = UUID.nameUUIDFromBytes(name.toByteArray()),
        override val itemType: VaultItemType = VaultItemType.Login,
        override val pinned: Boolean = false,
    ) : LiteItem

    private fun items(vararg names: String) = names.map { TestLiteItem(name = it) }

    private fun idsOf(vararg names: String) = names.mapTo(mutableSetOf()) { TestLiteItem(it).id }

    private fun List<LiteItem>.names() = map { it.name }

    @Test
    fun `no suggestions leaves the list untouched`() {
        val provided = items("Amazon", "GitHub", "Google")

        val result = provided.withSuggestedFirst(emptySet())

        assertSame(provided, result)
    }

    @Test
    fun `suggested items move to the front`() {
        val provided = items("Amazon", "GitHub", "Google")

        val result = provided.withSuggestedFirst(idsOf("GitHub"))

        assertEquals(listOf("GitHub", "Amazon", "Google"), result.names())
    }

    @Test
    fun `order within each group is preserved`() {
        val provided = items("Amazon", "GitHub", "Google", "GitHub (work)")

        val result = provided.withSuggestedFirst(idsOf("GitHub", "GitHub (work)"))

        assertEquals(
            listOf("GitHub", "GitHub (work)", "Amazon", "Google"),
            result.names(),
        )
    }

    @Test
    fun `ids that are not in the list are ignored`() {
        val provided = items("Amazon", "Google")

        val result = provided.withSuggestedFirst(idsOf("GitHub"))

        assertSame(provided, result)
    }

    @Test
    fun `a partially present suggestion set hoists only what the list holds`() {
        val provided = items("Amazon", "Google")

        val result = provided.withSuggestedFirst(idsOf("GitHub", "Google"))

        assertEquals(listOf("Google", "Amazon"), result.names())
    }

    @Test
    fun `an empty list stays empty`() {
        val result = emptyList<LiteItem>().withSuggestedFirst(idsOf("GitHub"))

        assertEquals(emptyList(), result)
    }
}
