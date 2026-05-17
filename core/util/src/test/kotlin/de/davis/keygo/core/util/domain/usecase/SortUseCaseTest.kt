package de.davis.keygo.core.util.domain.usecase

import kotlin.test.Test
import kotlin.test.assertEquals

class SortUseCaseTest {

    private val sort = SortUseCase()

    @Test
    fun `sorts ascending case-insensitively by selector`() {
        val result = sort(listOf("banana", "Apple", "cherry")) { it }
        assertEquals(listOf("Apple", "banana", "cherry"), result)
    }

    @Test
    fun `natural numeric ordering puts item2 before item10`() {
        val result = sort(listOf("item10", "item2", "item1")) { it }
        assertEquals(listOf("item1", "item2", "item10"), result)
    }

    @Test
    fun `descending reverses the natural order`() {
        val result = sort(listOf("item2", "item10", "item1"), ascending = false) { it }
        assertEquals(listOf("item10", "item2", "item1"), result)
    }

    @Test
    fun `blank and empty selector values sort first`() {
        val result = sort(listOf("b", "", "a")) { it }
        assertEquals(listOf("", "a", "b"), result)
    }

    @Test
    fun `sorts by a non-identity selector`() {
        data class Box(val name: String, val n: Int)
        val result = sort(listOf(Box("z", 1), Box("a", 2))) { it.name }
        assertEquals(listOf(Box("a", 2), Box("z", 1)), result)
    }

    @Test
    fun `equal sort keys preserve input order (stable)`() {
        data class Box(val name: String, val tag: Int)
        val result = sort(listOf(Box("x", 1), Box("x", 2), Box("x", 3))) { it.name }
        assertEquals(listOf(1, 2, 3), result.map { it.tag })
    }

    @Test
    fun `empty input returns empty list`() {
        assertEquals(emptyList(), sort(emptyList<String>()) { it })
    }
}
