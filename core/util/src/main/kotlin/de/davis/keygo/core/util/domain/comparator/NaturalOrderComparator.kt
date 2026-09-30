package de.davis.keygo.core.util.domain.comparator

import java.text.Collator

object NaturalOrderComparator : Comparator<String> {

    private val collator = ThreadLocal.withInitial {
        Collator.getInstance().apply { strength = Collator.PRIMARY }
    }

    private val CHUNK_REGEX = Regex("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)")

    override fun compare(o1: String?, o2: String?): Int =
        compareParts(o1?.split(CHUNK_REGEX).orEmpty(), o2?.split(CHUNK_REGEX).orEmpty())

    private fun compareParts(parts1: List<String>, parts2: List<String>): Int {
        val len = minOf(parts1.size, parts2.size)

        for (i in 0 until len) {
            val p1 = parts1[i]
            val p2 = parts2[i]

            // Guard against empty chunks produced by splitting blank/empty names
            if (p1.isEmpty() || p2.isEmpty()) return p1.length - p2.length

            val cmp = when {
                // split regex guarantees entire chunk is digits if first char is
                p1[0].isDigit() && p2[0].isDigit() -> compareNumeric(p1, p2)
                else -> collator.get()!!.compare(p1, p2) // locale-aware: ä, ö, ü, &, / etc.
            }

            if (cmp != 0) return cmp
        }

        return parts1.size.compareTo(parts2.size)
    }

    private fun compareNumeric(a: String, b: String): Int {
        val x = a.trimStart('0')
        val y = b.trimStart('0')
        // Without leading zeros, different lengths mean different magnitudes, so no parsing
        if (x.length != y.length) return x.length - y.length
        // Same length: lexicographic order == numeric order for digit-only strings
        return x.compareTo(y)
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
