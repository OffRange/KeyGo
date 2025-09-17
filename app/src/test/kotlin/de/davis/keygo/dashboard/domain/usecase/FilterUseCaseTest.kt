package de.davis.keygo.dashboard.domain.usecase

import de.davis.keygo.core.domain.model.crypto.CryptographicData
import de.davis.keygo.dashboard.domain.model.Filter
import de.davis.keygo.dashboard.domain.model.Filter.Direction
import kotlin.test.Test
import kotlin.test.assertEquals

class FilterUseCaseTest {

    private var useCase: FilterUseCase = FilterUseCase()
    private var filterAsc: Filter = Filter.Alphanumerical(direction = Direction.Ascending)
    private var filterDesc: Filter = Filter.Alphanumerical(direction = Direction.Descending)

    @Test
    fun `sorts items with numeric suffixes alphanumerically`() {
        val input = listOf(
            VaultItem.Basic(name = "AAA 10", encryptedData = CryptographicData.EMPTY, note = null),
            VaultItem.Basic(name = "AAA 8", encryptedData = CryptographicData.EMPTY, note = null),
            VaultItem.Basic(name = "AAA 9", encryptedData = CryptographicData.EMPTY, note = null),
        )
        val expected = listOf(
            VaultItem.Basic(name = "AAA 8", encryptedData = CryptographicData.EMPTY, note = null),
            VaultItem.Basic(name = "AAA 9", encryptedData = CryptographicData.EMPTY, note = null),
            VaultItem.Basic(name = "AAA 10", encryptedData = CryptographicData.EMPTY, note = null),
        )

        val actual = useCase(filterAsc, input)
        assertEquals(expected, actual)
    }

    @Test
    fun `sorts items with numeric suffixes alphanumerically desc`() {
        val input = listOf(
            VaultItem.Basic(name = "AAA 10", encryptedData = CryptographicData.EMPTY, note = null),
            VaultItem.Basic(name = "AAA 8", encryptedData = CryptographicData.EMPTY, note = null),
            VaultItem.Basic(name = "AAA 9", encryptedData = CryptographicData.EMPTY, note = null),
        )
        val expected = listOf(
            VaultItem.Basic(name = "AAA 10", encryptedData = CryptographicData.EMPTY, note = null),
            VaultItem.Basic(name = "AAA 9", encryptedData = CryptographicData.EMPTY, note = null),
            VaultItem.Basic(name = "AAA 8", encryptedData = CryptographicData.EMPTY, note = null),
        )

        val actual = useCase(filterDesc, input)
        assertEquals(expected, actual)
    }

    @Test
    fun `sorts items with leading zeros and multi-digit numbers`() {
        val input = listOf(
            VaultItem.Basic(name = "file2", encryptedData = CryptographicData.EMPTY, note = null),
            VaultItem.Basic(name = "file10", encryptedData = CryptographicData.EMPTY, note = null),
            VaultItem.Basic(name = "file1", encryptedData = CryptographicData.EMPTY, note = null),
            VaultItem.Basic(name = "file02", encryptedData = CryptographicData.EMPTY, note = null),
        )
        val expected = listOf(
            VaultItem.Basic(name = "file1", encryptedData = CryptographicData.EMPTY, note = null),
            VaultItem.Basic(name = "file2", encryptedData = CryptographicData.EMPTY, note = null),
            VaultItem.Basic(name = "file02", encryptedData = CryptographicData.EMPTY, note = null),
            VaultItem.Basic(name = "file10", encryptedData = CryptographicData.EMPTY, note = null)
        )

        val actual = useCase(filterAsc, input)
        assertEquals(expected, actual)
    }

    @Test
    fun `sorts items with leading numbers`() {
        val input = listOf(
            VaultItem.Basic(name = "2file", encryptedData = CryptographicData.EMPTY, note = null),
            VaultItem.Basic(name = "10file", encryptedData = CryptographicData.EMPTY, note = null),
            VaultItem.Basic(name = "1file", encryptedData = CryptographicData.EMPTY, note = null),
            VaultItem.Basic(name = "02file", encryptedData = CryptographicData.EMPTY, note = null),
        )
        val expected = listOf(
            VaultItem.Basic(name = "1file", encryptedData = CryptographicData.EMPTY, note = null),
            VaultItem.Basic(name = "2file", encryptedData = CryptographicData.EMPTY, note = null),
            VaultItem.Basic(name = "02file", encryptedData = CryptographicData.EMPTY, note = null),
            VaultItem.Basic(name = "10file", encryptedData = CryptographicData.EMPTY, note = null)
        )

        val actual = useCase(filterAsc, input)
        assertEquals(expected, actual)
    }

    @Test
    fun `empty list returns empty list`() {
        val actual = useCase(filterAsc, emptyList())
        assertEquals(emptyList<VaultItem.Basic>(), actual)
    }

    @Test
    fun `items without numeric parts are sorted lexically`() {
        val input = listOf(
            VaultItem.Basic(name = "banana", encryptedData = CryptographicData.EMPTY, note = null),
            VaultItem.Basic(name = "Apple", encryptedData = CryptographicData.EMPTY, note = null),
            VaultItem.Basic(name = "apple", encryptedData = CryptographicData.EMPTY, note = null),
            VaultItem.Basic(name = "Banana", encryptedData = CryptographicData.EMPTY, note = null),
            VaultItem.Basic(name = "cherry", encryptedData = CryptographicData.EMPTY, note = null)
        )
        val expected = listOf(
            VaultItem.Basic(name = "Apple", encryptedData = CryptographicData.EMPTY, note = null),
            VaultItem.Basic(name = "apple", encryptedData = CryptographicData.EMPTY, note = null),
            VaultItem.Basic(name = "banana", encryptedData = CryptographicData.EMPTY, note = null),
            VaultItem.Basic(name = "Banana", encryptedData = CryptographicData.EMPTY, note = null),
            VaultItem.Basic(name = "cherry", encryptedData = CryptographicData.EMPTY, note = null)
        )

        val actual = useCase(filterAsc, input)
        assertEquals(expected, actual)
    }
}
