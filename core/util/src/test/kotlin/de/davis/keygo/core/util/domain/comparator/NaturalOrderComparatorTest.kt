package de.davis.keygo.core.util.domain.comparator

import kotlin.math.sign
import kotlin.test.Test
import kotlin.test.assertEquals

class NaturalOrderComparatorTest {

    @Test
    fun `sorts ascending case-insensitively`() {
        val result = listOf("banana", "Apple", "cherry").sortedNaturally()
        assertEquals(listOf("Apple", "banana", "cherry"), result)
    }

    @Test
    fun `empty input returns empty list`() {
        assertEquals(emptyList(), listOf<String>().sortedNaturally())
    }

    @Test
    fun `single element list is returned unchanged`() {
        assertEquals(listOf("only"), listOf("only").sortedNaturally())
    }

    @Test
    fun `empty strings sort first`() {
        val result = listOf("b", "", "a").sortedNaturally()
        assertEquals(listOf("", "a", "b"), result)
    }

    @Test
    fun `shorter prefix sorts before longer string`() {
        val result = listOf("item1", "item", "itemA").sortedNaturally()
        assertEquals(listOf("item", "item1", "itemA"), result)
    }

    @Test
    fun `natural numeric ordering puts item2 before item10`() {
        val result = listOf("item10", "item2", "item1").sortedNaturally()
        assertEquals(listOf("item1", "item2", "item10"), result)
    }

    @Test
    fun `leading numbers are compared numerically`() {
        val result = listOf("10 apples", "2 apples", "1 apple").sortedNaturally()
        assertEquals(listOf("1 apple", "2 apples", "10 apples"), result)
    }

    @Test
    fun `pure numeric strings are compared numerically`() {
        val result = listOf("100", "20", "3", "1000").sortedNaturally()
        assertEquals(listOf("3", "20", "100", "1000"), result)
    }

    @Test
    fun `multiple numeric segments are each compared numerically`() {
        val result = listOf("v1.10.0", "v1.2.10", "v1.2.0", "v2.0.0").sortedNaturally()
        assertEquals(listOf("v1.2.0", "v1.2.10", "v1.10.0", "v2.0.0"), result)
    }

    @Test
    fun `numbers larger than Long do not overflow`() {
        val huge = "file" + "9".repeat(30)
        val bigger = "file1" + "0".repeat(30)
        val result = listOf(bigger, huge, "file1").sortedNaturally()
        assertEquals(listOf("file1", huge, bigger), result)
    }

    @Test
    fun `leading zeros do not inflate magnitude`() {
        val result = listOf("item10", "item007", "item2").sortedNaturally()
        assertEquals(listOf("item2", "item007", "item10"), result)
    }

    @Test
    fun `numbers differing only in leading zeros compare as equal`() {
        assertEquals(0, NaturalOrderComparator.compare("a7", "a007"))
    }

    @Test
    fun `leading-zero-only duplicates keep input order`() {
        assertEquals(listOf("a007", "a7", "a07"), listOf("a007", "a7", "a07").sortedNaturally())
        assertEquals(listOf("a07", "a007", "a7"), listOf("a07", "a007", "a7").sortedNaturally())
    }

    @Test
    fun `all-zero numbers sort before non-zero ones`() {
        val result = listOf("x5", "x000", "x1").sortedNaturally()
        assertEquals(listOf("x000", "x1", "x5"), result)
    }

    @Test
    fun `case differences do not break numeric ordering`() {
        val result = listOf("file10", "File2", "FILE1").sortedNaturally()
        assertEquals(listOf("FILE1", "File2", "file10"), result)
    }

    @Test
    fun `strings differing only in case compare as equal`() {
        assertEquals(0, NaturalOrderComparator.compare("Keygo", "KEYGO"))
    }

    @Test
    fun `case-only duplicates keep input order`() {
        assertEquals(listOf("a", "A", "b"), listOf("b", "a", "A").sortedNaturally())
        assertEquals(listOf("A", "a", "b"), listOf("b", "A", "a").sortedNaturally())
    }

    @Test
    fun `umlauts sort alongside their base letter`() {
        // Assumes a default locale such as de or en (not e.g. sv, where Ä follows Z)
        val result = listOf("Zebra", "Äpfel", "Birne").sortedNaturally()
        assertEquals(listOf("Äpfel", "Birne", "Zebra"), result)
    }

    @Test
    fun `accented and unaccented letters compare as equal`() {
        assertEquals(0, NaturalOrderComparator.compare("Muller", "Müller"))
    }

    @Test
    fun `null compares equal to null`() {
        assertEquals(0, NaturalOrderComparator.compare(null, null))
    }

    @Test
    fun `null sorts before non-null values`() {
        assert(NaturalOrderComparator.compare(null, "a") < 0)
        assert(NaturalOrderComparator.compare("a", null) > 0)
    }

    private val samples = listOf(
        "", "a", "A", "b", "item", "item1", "item2", "item10", "Item10",
        "10", "2", "v1.2.0", "v1.10.0", "Äpfel", "apfel", "x9y", "x10y",
        "x09y", "x009y", "item02", "0", "000",
        "x٣", "٣", "x5", "٣x",
    )

    @Test
    fun `comparison is antisymmetric`() {
        for (a in samples) for (b in samples) {
            val ab = NaturalOrderComparator.compare(a, b).sign
            val ba = NaturalOrderComparator.compare(b, a).sign
            assertEquals(-ab, ba, "compare($a, $b) and compare($b, $a) disagree")
        }
    }

    @Test
    fun `comparison is transitive`() {
        for (a in samples) for (b in samples) for (c in samples) {
            val ab = NaturalOrderComparator.compare(a, b)
            val bc = NaturalOrderComparator.compare(b, c)
            if (ab <= 0 && bc <= 0) {
                assert(NaturalOrderComparator.compare(a, c) <= 0) {
                    "Transitivity violated for $a <= $b <= $c"
                }
            }
        }
    }

    @Test
    fun `every string compares equal to itself`() {
        samples.forEach { assertEquals(0, NaturalOrderComparator.compare(it, it), "for '$it'") }
    }

    @Test
    fun `sorting is idempotent`() {
        val once = samples.shuffled().sortedNaturally()
        assertEquals(once, once.sortedNaturally())
    }

    @Test
    fun `descending reverses the natural order`() {
        val result = listOf("item2", "item10", "item1").sortedNaturallyDescending()
        assertEquals(listOf("item10", "item2", "item1"), result)
    }

    @Test
    fun `descending is the exact reverse of ascending for distinct keys`() {
        val input = listOf("b10", "a2", "c1", "a10", "b2")
        assertEquals(input.sortedNaturally().reversed(), input.sortedNaturallyDescending())
    }

    private data class Box(val name: String, val tag: Int)

    @Test
    fun `sorts by a non-identity selector`() {
        val result = listOf(Box("z", 1), Box("a", 2)).sortedNaturallyBy { it.name }
        assertEquals(listOf(Box("a", 2), Box("z", 1)), result)
    }

    @Test
    fun `selector uses natural numeric ordering`() {
        val input = listOf(Box("Key 10", 1), Box("Key 2", 2), Box("Key 1", 3))
        val result = input.sortedNaturallyBy { it.name }
        assertEquals(listOf(3, 2, 1), result.map { it.tag })
    }

    @Test
    fun `descending selector reverses order`() {
        val input = listOf(Box("Key 2", 1), Box("Key 10", 2), Box("Key 1", 3))
        val result = input.sortedNaturallyByDescending { it.name }
        assertEquals(listOf(2, 1, 3), result.map { it.tag })
    }

    @Test
    fun `equal sort keys preserve input order (stable)`() {
        val result = listOf(Box("x", 1), Box("x", 2), Box("x", 3)).sortedNaturallyBy { it.name }
        assertEquals(listOf(1, 2, 3), result.map { it.tag })
    }

    @Test
    fun `keys equal under collator preserve input order (stable)`() {
        val input = listOf(Box("KEY", 1), Box("key", 2), Box("Key", 3))
        val result = input.sortedNaturallyBy { it.name }
        assertEquals(listOf(1, 2, 3), result.map { it.tag })
    }

    @Test
    fun `sortedNaturally does not mutate the original list`() {
        val input = mutableListOf("b", "a")
        input.sortedNaturally()
        assertEquals(listOf("b", "a"), input)
    }

    @Test
    fun `sortNaturally sorts a mutable list in place`() {
        val list = mutableListOf("item10", "item2", "item1")
        list.sortNaturally()
        assertEquals(listOf("item1", "item2", "item10"), list)
    }
}
