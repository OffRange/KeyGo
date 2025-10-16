package de.davis.keygo.dashboard.domain.usecase

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.lite.LiteItem
import de.davis.keygo.dashboard.domain.model.Filter
import de.davis.keygo.dashboard.domain.model.Filter.Direction
import kotlin.test.Test
import kotlin.test.assertEquals

class FilterUseCaseTest {

    private var useCase: FilterUseCase = FilterUseCase()
    private var filterAsc: Filter = Filter.Alphanumerical(direction = Direction.Ascending)
    private var filterDesc: Filter = Filter.Alphanumerical(direction = Direction.Descending)

    data class TestLiteItem(
        override val vaultItemId: ItemId = 0,
        override val name: String,
    ) : LiteItem

    @Test
    fun `sorts items with numeric suffixes alphanumerically`() {
        val input = listOf(
            TestLiteItem(name = "AAA 10"),
            TestLiteItem(name = "AAA 8"),
            TestLiteItem(name = "AAA 9"),
        )
        val expected = listOf(
            TestLiteItem(name = "AAA 8"),
            TestLiteItem(name = "AAA 9"),
            TestLiteItem(name = "AAA 10"),
        )

        val actual = useCase(filterAsc, input)
        assertEquals(expected, actual)
    }

    @Test
    fun `sorts items with numeric suffixes alphanumerically desc`() {
        val input = listOf(
            TestLiteItem(name = "AAA 10"),
            TestLiteItem(name = "AAA 8"),
            TestLiteItem(name = "AAA 9"),
        )
        val expected = listOf(
            TestLiteItem(name = "AAA 10"),
            TestLiteItem(name = "AAA 9"),
            TestLiteItem(name = "AAA 8"),
        )

        val actual = useCase(filterDesc, input)
        assertEquals(expected, actual)
    }

    @Test
    fun `sorts items with leading zeros and multi-digit numbers`() {
        val input = listOf(
            TestLiteItem(name = "file2"),
            TestLiteItem(name = "file10"),
            TestLiteItem(name = "file1"),
            TestLiteItem(name = "file02"),
        )
        val expected = listOf(
            TestLiteItem(name = "file1"),
            TestLiteItem(name = "file2"),
            TestLiteItem(name = "file02"),
            TestLiteItem(name = "file10")
        )

        val actual = useCase(filterAsc, input)
        assertEquals(expected, actual)
    }

    @Test
    fun `sorts items with leading numbers`() {
        val input = listOf(
            TestLiteItem(name = "2file"),
            TestLiteItem(name = "10file"),
            TestLiteItem(name = "1file"),
            TestLiteItem(name = "02file"),
        )
        val expected = listOf(
            TestLiteItem(name = "1file"),
            TestLiteItem(name = "2file"),
            TestLiteItem(name = "02file"),
            TestLiteItem(name = "10file")
        )

        val actual = useCase(filterAsc, input)
        assertEquals(expected, actual)
    }

    @Test
    fun `empty list returns empty list`() {
        val actual = useCase(filterAsc, emptyList())
        assertEquals(emptyList<TestLiteItem>(), actual)
    }

    @Test
    fun `items without numeric parts are sorted lexically`() {
        val input = listOf(
            TestLiteItem(name = "banana"),
            TestLiteItem(name = "Apple"),
            TestLiteItem(name = "apple"),
            TestLiteItem(name = "Banana"),
            TestLiteItem(name = "cherry")
        )
        val expected = listOf(
            TestLiteItem(name = "Apple"),
            TestLiteItem(name = "apple"),
            TestLiteItem(name = "banana"),
            TestLiteItem(name = "Banana"),
            TestLiteItem(name = "cherry")
        )

        val actual = useCase(filterAsc, input)
        assertEquals(expected, actual)
    }
}
