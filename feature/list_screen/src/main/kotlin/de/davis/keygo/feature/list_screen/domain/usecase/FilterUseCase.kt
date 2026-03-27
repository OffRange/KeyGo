package de.davis.keygo.feature.list_screen.domain.usecase

import de.davis.keygo.core.item.domain.model.lite.LiteItem
import de.davis.keygo.feature.list_screen.domain.model.Filter
import org.koin.core.annotation.Single
import java.text.Collator

@Single
class FilterUseCase {


    private val collator = Collator.getInstance().apply {
        strength = Collator.PRIMARY // case-insensitive
    }

    operator fun <I : LiteItem> invoke(filter: Filter, namedItems: List<I>): List<I> {
        return when (filter) {
            is Filter.Alphanumerical -> {
                val prepared = namedItems.map { item ->
                    item to item.name.split(CHUNK_REGEX)
                }

                val comp = compareByAlphanumeric<Pair<I, Parts>>(filter.direction) { it.second }
                prepared.sortedWith(comp).map { it.first }
            }
        }
    }

    private fun <T> compareByAlphanumeric(
        direction: Filter.Direction,
        selector: (T) -> Parts
    ): Comparator<T> = Comparator { a, b ->
        val cmp = compareParts(selector(a), selector(b))
        if (direction is Filter.Direction.Descending) -cmp else cmp
    }

    private fun compareParts(parts1: Parts, parts2: Parts): Int {
        val len = minOf(parts1.size, parts2.size)

        for (i in 0 until len) {
            val p1 = parts1[i]
            val p2 = parts2[i]

            // Guard against empty chunks produced by splitting blank/empty names
            if (p1.isEmpty() || p2.isEmpty()) return p1.length - p2.length

            val cmp = when {
                // split regex guarantees entire chunk is digits if first char is
                p1[0].isDigit() && p2[0].isDigit() -> compareNumeric(p1, p2)
                else -> collator.compare(p1, p2) // locale-aware: handles ä, ö, ü, &, / etc.
            }

            if (cmp != 0) return cmp
        }

        return parts1.size.compareTo(parts2.size)
    }

    private fun compareNumeric(a: String, b: String): Int {
        // Fast path: different lengths means different magnitudes — no parsing needed
        if (a.length != b.length) return a.length - b.length

        // Same length: lexicographic order == numeric order for digit-only strings
        return a.compareTo(b)
    }

    companion object {

        private typealias Parts = List<String>

        private val CHUNK_REGEX = Regex("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)")
    }
}