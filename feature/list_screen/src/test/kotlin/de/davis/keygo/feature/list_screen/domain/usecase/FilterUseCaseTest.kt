package de.davis.keygo.feature.list_screen.domain.usecase

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.item.domain.model.lite.LiteItem
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.feature.list_screen.domain.model.FilterState
import de.davis.keygo.feature.list_screen.domain.model.SortDirection
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FilterUseCaseTest {

    private val useCase: FilterUseCase = FilterUseCase()

    private val filterStateAsc = FilterState(sortDirection = SortDirection.Ascending)
    private val filterStateDesc = FilterState(sortDirection = SortDirection.Descending)

    data class TestLiteItem(
        override val name: String,
        override val id: ItemId = UUID.nameUUIDFromBytes(name.toByteArray()),
        override val itemType: VaultItemType = VaultItemType.Login,
        override val pinned: Boolean = false,
    ) : LiteItem

    private fun items(vararg names: String) = names.map { TestLiteItem(name = it) }

    private inner class SortAssertion(private val items: List<String>) {

        fun ascProduces(vararg expected: String) {
            val provided = items(*items.toTypedArray())
            val expected = items(*expected)
            val result = useCase(filterStateAsc, provided, emptyMap())

            assertEquals(expected, result)
        }

        fun descProduces(vararg expected: String) {
            val provided = items(*items.toTypedArray())
            val expected = items(*expected)
            val result = useCase(filterStateDesc, provided, emptyMap())

            assertEquals(expected, result)
        }
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
    fun `filtering an empty list returns empty list`() {
        val state = FilterState(selectedScores = setOf(PasswordScore.Weak))
        val result = useCase(state, emptyList<TestLiteItem>(), noScores)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `items without numeric parts are sorted lexically`() {
        val result = useCase(
            filterStateAsc,
            items("cherry", "Banana", "apple", "Apple", "banana"),
            emptyMap()
        )

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
    fun `identical names are stable - order preserved`() {
        val result = useCase(filterStateAsc, items("same", "same", "same"), emptyMap())
        assertEquals(3, result.size)
        assertTrue(result.all { it.name == "same" })
    }

    // Edge: special characters ─────────────────────────────────────────────
    @Test
    fun `names starting with special characters sort before letters`() {
        val result = useCase(filterStateAsc, items("banana", "apple", "/path", "&tag"), emptyMap())
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
        val result1 = useCase(filterStateAsc, input, emptyMap())
        val result2 = useCase(filterStateAsc, input, emptyMap())
        assertEquals(result1, result2)
    }

    @Test
    fun `backslash and forward slash in names returns all items`() {
        val input = items("aab", "a/b", "a&b", "a\\b")
        val result = useCase(filterStateAsc, input, emptyMap())
        assertEquals(4, result.size)
        assertTrue(result.map { it.name }.containsAll(input.map { it.name }))
    }

    @Test
    fun `names with only special characters - descending is reverse of ascending`() {
        val input = items("///", "&&&", "\\\\", "###")
        val asc = useCase(filterStateAsc, input, emptyMap())
        val desc = useCase(filterStateDesc, input, emptyMap())
        assertEquals(asc.reversed(), desc)
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
        assertEquals(3, useCase(filterStateAsc, items("normal", " ", ""), emptyMap()).size)
    }

    // Edge: case sensitivity
    @Test
    fun `uppercase and lowercase letters with same prefix are grouped before later letters`() {
        val result =
            useCase(filterStateAsc, items("Banana", "banana", "apple", "Apple"), emptyMap())
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
        val asc = useCase(filterStateAsc, input, emptyMap())
        val desc = useCase(filterStateDesc, input, emptyMap())
        assertEquals(asc.reversed(), desc)
    }

    // Edge: mixed alpha, numeric, special
    @Test
    fun `fully mixed names produce deterministic order`() {
        val input = items("10", "abc", "/path", "2file", "FILE3", "&special", "1")
        val result1 = useCase(filterStateAsc, input, emptyMap())
        val result2 = useCase(filterStateAsc, input, emptyMap())

        assertEquals(result1, result2)
    }

    @Test
    fun `names with emoji do not crash`() {
        assertEquals(3, useCase(filterStateAsc, items("banana", "🍌2", "🍎 apple"), emptyMap()).size)
    }

    // Filter by item type
    private val typedItems = listOf(
        TestLiteItem(name = "Login A", id = newItemId(), itemType = VaultItemType.Login),
        TestLiteItem(name = "Login B", id = newItemId(), itemType = VaultItemType.Login),
    )

    private val noScores: Map<ItemId, PasswordScore> = emptyMap()

    @Test
    fun `no item type selected returns all items`() {
        val state = FilterState(selectedItemTypes = emptySet())
        val result = useCase(state, typedItems, noScores)
        assertEquals(result, typedItems)
    }

    @Test
    fun `selecting Password type keeps only passwords`() {
        val state = FilterState(selectedItemTypes = setOf(VaultItemType.Login))
        val result = useCase(state, typedItems, noScores)

        assertTrue(result.all { it.itemType == VaultItemType.Login })
    }

    // Filter by score
    private val scoredItems = listOf(
        TestLiteItem(name = "Excellent PW", id = newItemId()),
        TestLiteItem(name = "No Score", id = newItemId()),
        TestLiteItem(name = "Strong PW", id = newItemId()),
        TestLiteItem(name = "Weak PW", id = newItemId()),
    )

    private val scores = mapOf(
        scoredItems[0].id to PasswordScore.Excellent,
        scoredItems[2].id to PasswordScore.Strong,
        scoredItems[3].id to PasswordScore.Weak,
    )

    @Test
    fun `no score selected returns all passwords items`() {
        val state = FilterState(selectedScores = emptySet())
        val result =
            useCase(state, scoredItems, scores).filter { it.itemType == VaultItemType.Login }
        val expected = scoredItems.filter { it.itemType == VaultItemType.Login }
        assertEquals(expected, result)
    }

    @Test
    fun `selecting single score returns matching items`() {
        val state = FilterState(selectedScores = setOf(PasswordScore.Weak))
        val result = useCase(state, scoredItems, scores)

        val expected = scoredItems.filter { scores[it.id] == PasswordScore.Weak }
        assertEquals(expected, result)
    }

    @Test
    fun `selecting multiple scores returns union of matches`() {
        val state = FilterState(
            selectedScores = setOf(PasswordScore.Weak, PasswordScore.Excellent),
        )
        val result = useCase(state, scoredItems, scores)

        val expected = scoredItems.filter {
            scores[it.id] == PasswordScore.Weak ||
                    scores[it.id] == PasswordScore.Excellent
        }

        assertEquals(expected.size, result.size)
        assertTrue(result.containsAll(expected))
    }

    @Test
    fun `selecting a score not present in any item returns empty list`() {
        val state = FilterState(selectedScores = setOf(PasswordScore.Ridiculous))
        val result = useCase(state, scoredItems, scores)
        assertTrue(result.isEmpty())
    }

    // Combined filters
    @Test
    fun `item type and score filters are applied together`() {
        val state = FilterState(
            selectedItemTypes = setOf(VaultItemType.Login),
            selectedScores = setOf(PasswordScore.Excellent),
        )
        val result = useCase(state, scoredItems, scores)
        assertTrue(result.all { it.itemType == VaultItemType.Login })
        assertTrue(result.all { scores[it.id] == PasswordScore.Excellent })
    }

    @Test
    fun `filter result is still sorted by sort direction`() {
        val state = FilterState(
            sortDirection = SortDirection.Descending,
            selectedScores = setOf(PasswordScore.Weak, PasswordScore.Strong),
        )
        val result = useCase(state, scoredItems, scores)

        val ascState = state.copy(sortDirection = SortDirection.Ascending)
        val ascResult = useCase(ascState, scoredItems, scores)

        assertEquals(ascResult.reversed(), result)
    }

    // Pinned filter & sorting
    private val mixedPinnedItems = listOf(
        TestLiteItem(name = "Charlie", id = newItemId(), pinned = false),
        TestLiteItem(name = "Alpha", id = newItemId(), pinned = true),
        TestLiteItem(name = "Bravo", id = newItemId(), pinned = true),
        TestLiteItem(name = "Delta", id = newItemId(), pinned = false),
    )

    @Test
    fun `pinned items appear before unpinned items`() {
        val result = useCase(filterStateAsc, mixedPinnedItems, noScores)

        val pinnedNames = result.takeWhile { it.pinned }.map { it.name }
        val unpinnedNames = result.dropWhile { it.pinned }.map { it.name }

        assertEquals(listOf("Alpha", "Bravo"), pinnedNames)
        assertEquals(listOf("Charlie", "Delta"), unpinnedNames)
    }

    @Test
    fun `pinned items appear before unpinned items in descending order`() {
        val result = useCase(filterStateDesc, mixedPinnedItems, noScores)

        val pinnedNames = result.takeWhile { it.pinned }.map { it.name }
        val unpinnedNames = result.dropWhile { it.pinned }.map { it.name }

        assertEquals(listOf("Bravo", "Alpha"), pinnedNames)
        assertEquals(listOf("Delta", "Charlie"), unpinnedNames)
    }

    @Test
    fun `onlyPinned filter excludes unpinned items`() {
        val state = FilterState(onlyPinned = true)
        val result = useCase(state, mixedPinnedItems, noScores)

        assertTrue(result.all { it.pinned })
        assertEquals(2, result.size)
    }

    @Test
    fun `onlyPinned false returns all items`() {
        val state = FilterState(onlyPinned = false)
        val result = useCase(state, mixedPinnedItems, noScores)

        assertEquals(4, result.size)
    }

    @Test
    fun `onlyPinned with no pinned items returns empty list`() {
        val state = FilterState(onlyPinned = true)
        val unpinnedOnly = items("A", "B", "C")
        val result = useCase(state, unpinnedOnly, noScores)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `onlyPinned combined with item type filter`() {
        val state = FilterState(
            onlyPinned = true,
            selectedItemTypes = setOf(VaultItemType.Login),
        )
        val result = useCase(state, mixedPinnedItems, noScores)

        assertTrue(result.all { it.pinned })
        assertTrue(result.all { it.itemType == VaultItemType.Login })
    }

    @Test
    fun `all items pinned preserves sort order`() {
        val allPinned = listOf(
            TestLiteItem(name = "Zulu", pinned = true),
            TestLiteItem(name = "Alpha", pinned = true),
            TestLiteItem(name = "Mike", pinned = true),
        )
        val result = useCase(filterStateAsc, allPinned, noScores)

        assertEquals(listOf("Alpha", "Mike", "Zulu"), result.map { it.name })
    }
}