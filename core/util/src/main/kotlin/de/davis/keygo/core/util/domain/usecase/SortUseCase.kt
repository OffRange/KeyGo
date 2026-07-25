package de.davis.keygo.core.util.domain.usecase

import org.koin.core.annotation.Single
import java.text.Collator

@Single
class SortUseCase {

    private val collator = ThreadLocal.withInitial {
        Collator.getInstance().apply {
            strength = Collator.PRIMARY // case- and accent-insensitive grouping
        }
    }

    operator fun <T> invoke(
        items: Iterable<T>,
        ascending: Boolean = true,
        selector: (T) -> String,
    ): List<T> {
        val prepared = items.map { item ->
            item to selector(item).split(CHUNK_REGEX)
        }

        val comparator = Comparator<Pair<T, List<String>>> { a, b ->
            val cmp = compareParts(a.second, b.second)
            if (ascending) cmp else -cmp
        }

        return prepared.sortedWith(comparator).map { it.first }
    }

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
        // Fast path: different lengths mean different magnitudes, so no parsing
        if (a.length != b.length) return a.length - b.length
        // Same length: lexicographic order == numeric order for digit-only strings
        return a.compareTo(b)
    }

    companion object {
        private val CHUNK_REGEX = Regex("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)")
    }
}
