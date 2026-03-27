package de.davis.keygo.dashboard.domain.usecase

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.lite.LiteItem
import de.davis.keygo.feature.list_screen.domain.model.Filter
import de.davis.keygo.feature.list_screen.domain.model.Filter.Direction
import de.davis.keygo.feature.list_screen.domain.usecase.FilterUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FilterUseCaseTest {

    private var useCase: FilterUseCase = FilterUseCase()
    private var filterAsc: Filter = Filter.Alphanumerical(direction = Direction.Ascending)
    private var filterDesc: Filter = Filter.Alphanumerical(direction = Direction.Descending)

    data class TestLiteItem(
        override val vaultItemId: ItemId = 0,
        override val name: String,
    ) : LiteItem

    private fun items(vararg names: String) = names.map { TestLiteItem(name = it) }

    private inner class SortAssertion(private val inputNames: List<String>) {
        fun ascProduces(vararg expected: String) =
            assertEquals(items(*expected), useCase(filterAsc, items(*inputNames.toTypedArray())))

        fun descProduces(vararg expected: String) =
            assertEquals(items(*expected), useCase(filterDesc, items(*inputNames.toTypedArray())))
    }

    private fun sorting(vararg names: String) = SortAssertion(names.toList())

    @Test
    fun `sorts items with numeric suffixes alphanumerically`() = sorting("AAA 10", "AAA 8", "AAA 9")
        .ascProduces("AAA 8", "AAA 9", "AAA 10")

    @Test
    fun `sorts items with numeric suffixes alphanumerically desc`() =
        sorting("AAA 8", "AAA 9", "AAA 10").descProduces("AAA 10", "AAA 9", "AAA 8")

    @Test
    fun `sorts items with leading zeros and multi-digit numbers`() =
        sorting("file10", "file02", "file2", "file1")
            .ascProduces("file1", "file2", "file02", "file10")

    @Test
    fun `sorts items with leading numbers`() = sorting("10file", "02file", "2file", "1file")
        .ascProduces("1file", "2file", "02file", "10file")

    @Test
    fun `empty list returns empty list`() {
        assertEquals(emptyList<TestLiteItem>(), useCase(filterAsc, emptyList()))
    }

    @Test
    fun `items without numeric parts are sorted lexically`() {
        val result = useCase(filterAsc, items("cherry", "Banana", "apple", "Apple", "banana"))

        // At Collator.PRIMARY, Apple/apple are equal and banana/Banana are equal —
        // only assert the group ordering, not internal order within equal-strength items
        val lastAppleIndex = maxOf(
            result.indexOfFirst { it.name == "Apple" },
            result.indexOfFirst { it.name == "apple" }
        )
        val firstBananaIndex = minOf(
            result.indexOfFirst { it.name == "banana" },
            result.indexOfFirst { it.name == "Banana" }
        )
        val cherryIndex = result.indexOfFirst { it.name == "cherry" }

        assertTrue(lastAppleIndex < firstBananaIndex)
        assertTrue(firstBananaIndex < cherryIndex)
        assertEquals(5, result.size)
    }

    // Edge: single & duplicates
    @Test
    fun `single item list returns same item`() =
        sorting("only item").ascProduces("only item")

    @Test
    fun `duplicate names preserve all entries`() {
        assertEquals(3, useCase(filterAsc, items("AAA", "AAA", "AAA")).size)
    }

    @Test
    fun `identical names are stable - order preserved`() {
        val result = useCase(filterAsc, items("same", "same", "same"))
        assertEquals(3, result.size)
        assertTrue(result.all { it.name == "same" })
    }

    // Edge: special characters ─────────────────────────────────────────────
    @Test
    fun `names starting with special characters sort before letters`() {
        val result = useCase(filterAsc, items("banana", "apple", "/path", "&tag"))
        val lastSpecialIndex = maxOf(
            result.indexOfFirst { it.name == "/path" },
            result.indexOfFirst { it.name == "&tag" }
        )
        val firstAlphaIndex = minOf(
            result.indexOfFirst { it.name == "apple" },
            result.indexOfFirst { it.name == "banana" }
        )
        assertTrue(lastSpecialIndex < firstAlphaIndex)
    }

    @Test
    fun `special characters mixed with numbers produce deterministic order`() {
        val input = items("&2item", "/1item", "#3item")
        assertEquals(useCase(filterAsc, input), useCase(filterAsc, input))
    }

    @Test
    fun `backslash and forward slash in names returns all items`() {
        val input = items("aab", "a/b", "a&b", "a\\b")
        val result = useCase(filterAsc, input)
        assertEquals(4, result.size)
        assertTrue(result.map { it.name }.containsAll(input.map { it.name }))
    }

    @Test
    fun `names with only special characters - descending is reverse of ascending`() {
        val input = items("///", "&&&", "\\\\", "###")
        val asc = useCase(filterAsc, input)
        assertEquals(asc.reversed(), useCase(filterDesc, input))
    }

    // Edge: numbers ────────────────────────────────────────────────────────
    @Test
    fun `names that are purely numeric`() =
        sorting("100", "9", "10", "2", "1")
            .ascProduces("1", "2", "9", "10", "100")

    @Test
    fun `large numbers do not overflow`() =
        sorting("file100000000000", "file99999999999", "file99999999998")
            .ascProduces("file99999999998", "file99999999999", "file100000000000")

    @Test
    fun `zero is sorted before positive numbers`() =
        sorting("item2", "item0", "item1")
            .ascProduces("item0", "item1", "item2")

    @Test
    fun `numbers with multiple digit segments sort by each segment`() =
        sorting("v2.0", "v1.10", "v1.2", "v1.9")
            .ascProduces("v1.2", "v1.9", "v1.10", "v2.0")

    @Test
    fun `number-only names sort numerically not lexically`() =
        sorting("100", "10", "2", "1")
            .ascProduces("1", "2", "10", "100")

    // Edge: whitespace
    @Test
    fun `names with internal whitespace sort correctly`() =
        sorting("a c", "a a", "a b")
            .ascProduces("a a", "a b", "a c")

    @Test
    fun `blank and empty names do not crash`() {
        assertEquals(3, useCase(filterAsc, items("normal", " ", "")).size)
    }

    // Edge: case sensitivity
    @Test
    fun `uppercase and lowercase letters with same prefix are grouped before later letters`() {
        val result = useCase(filterAsc, items("Banana", "banana", "apple", "Apple"))
        val lastAppleIndex = maxOf(
            result.indexOfFirst { it.name == "apple" },
            result.indexOfFirst { it.name == "Apple" }
        )
        val firstBananaIndex = minOf(
            result.indexOfFirst { it.name == "banana" },
            result.indexOfFirst { it.name == "Banana" }
        )
        assertTrue(lastAppleIndex < firstBananaIndex)
    }

    @Test
    fun `mixed case with numbers`() = sorting("ITEM10", "item2", "Item1")
        .ascProduces("Item1", "item2", "ITEM10")

    // Edge: descending mirrors ascending
    @Test
    fun `descending is exact reverse of ascending for unique names`() {
        val input = items("file2", "file10", "file1", "abc", "10abc")
        assertEquals(useCase(filterAsc, input).reversed(), useCase(filterDesc, input))
    }

    // Edge: mixed alpha, numeric, special
    @Test
    fun `fully mixed names produce deterministic order`() {
        val input = items("10", "abc", "/path", "2file", "FILE3", "&special", "1")
        assertEquals(useCase(filterAsc, input), useCase(filterAsc, input))
    }

    @Test
    fun `names with emoji do not crash`() {
        assertEquals(3, useCase(filterAsc, items("banana", "🍌2", "🍎 apple")).size)
    }
}
