package de.davis.keygo.core.util.domain.comparator

import java.text.Collator

object NaturalOrderComparator : Comparator<String> {

    // Locale-aware for text runs: ä, ö, ü, &, / etc.
    private val collator = ThreadLocal.withInitial {
        Collator.getInstance().apply { strength = Collator.PRIMARY }
    }

    // Walks both strings run by run (a run is all digits or no digits) instead of splitting them,
    // since this runs O(n log n) times per sort
    override fun compare(o1: String?, o2: String?): Int {
        if (o1 == null || o2 == null) return compareValues(o1, o2)

        var i = 0
        var j = 0
        while (i < o1.length && j < o2.length) {
            val digits1 = o1[i].isAsciiDigit()
            val digits2 = o2[j].isAsciiDigit()
            val end1 = runEnd(o1, i, digits1)
            val end2 = runEnd(o2, j, digits2)

            val cmp = when {
                digits1 && digits2 -> compareNumeric(o1, i, end1, o2, j, end2)
                // Shared runs ("Server " in "Server 2" vs "Server 10") are common and the collator
                // plus its substrings is the slow part, so identical runs skip it. Only exact
                // equality may: any looser shortcut (ASCII order, ignoreCase) disagrees with the
                // collator somewhere and breaks transitivity
                end1 - i == end2 - j && o1.regionMatches(i, o2, j, end1 - i) -> 0
                else -> collator.get()!!.compare(o1.substring(i, end1), o2.substring(j, end2))
            }

            if (cmp != 0) return cmp
            i = end1
            j = end2
        }

        // Whoever has runs left is longer: "item" < "item1"
        return (o1.length - i).compareTo(o2.length - j)
    }

    private fun Char.isAsciiDigit() = this in '0'..'9'

    private fun runEnd(s: String, from: Int, digits: Boolean): Int {
        var k = from
        while (k < s.length && s[k].isAsciiDigit() == digits) k++
        return k
    }

    private fun compareNumeric(
        a: String,
        aStart: Int,
        aEnd: Int,
        b: String,
        bStart: Int,
        bEnd: Int
    ): Int {
        var i = aStart
        while (i < aEnd && a[i] == '0') i++
        var j = bStart
        while (j < bEnd && b[j] == '0') j++

        // Without leading zeros, different lengths mean different magnitudes, so no parsing
        val lengthDiff = (aEnd - i) - (bEnd - j)
        if (lengthDiff != 0) return lengthDiff

        // Same length: digit-by-digit order == numeric order
        while (i < aEnd) {
            val d = a[i++] - b[j++]
            if (d != 0) return d
        }
        return 0
    }
}

fun Iterable<String>.sortedNaturally(): List<String> = sortedWith(NaturalOrderComparator)

fun Iterable<String>.sortedNaturallyDescending(): List<String> =
    sortedWith(NaturalOrderComparator.reversed())

inline fun <T> Iterable<T>.sortedNaturallyBy(crossinline selector: (T) -> String): List<T> =
    sortedWith(compareBy(NaturalOrderComparator, selector))

inline fun <T> Iterable<T>.sortedNaturallyByDescending(crossinline selector: (T) -> String): List<T> =
    sortedWith(compareByDescending(NaturalOrderComparator, selector))

fun MutableList<String>.sortNaturally() = sortWith(NaturalOrderComparator)
