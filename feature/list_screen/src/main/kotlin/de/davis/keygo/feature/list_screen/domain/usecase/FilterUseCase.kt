package de.davis.keygo.feature.list_screen.domain.usecase

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.Password
import de.davis.keygo.core.item.domain.model.lite.LiteItem
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.feature.list_screen.domain.model.FilterState
import de.davis.keygo.feature.list_screen.domain.model.SortDirection
import org.koin.core.annotation.Single
import java.text.Collator

@Single
class FilterUseCase {

    private val collator = Collator.getInstance().apply {
        strength = Collator.PRIMARY // case-insensitive
    }

    operator fun <I : LiteItem> invoke(
        filterState: FilterState,
        items: List<I>,
        passwordScores: Map<ItemId, Password.Score>,
    ): List<I> {
        val filtered = items.filter { item ->
            matchesItemType(filterState, item) &&
                    matchesScore(filterState, item, passwordScores) &&
                    matchesPinnedState(filterState, item)
        }

        val (pinned, unpinned) = filtered.partition { it.pinned }
        return sort(filterState.sortDirection, pinned) +
                sort(filterState.sortDirection, unpinned)
    }

    private fun matchesPinnedState(filterState: FilterState, item: LiteItem): Boolean =
        !filterState.onlyPinned || item.pinned

    private fun matchesItemType(filterState: FilterState, item: LiteItem): Boolean =
        filterState.selectedItemTypes.isEmpty() || item.itemType in filterState.selectedItemTypes

    private fun matchesScore(
        filterState: FilterState,
        item: LiteItem,
        passwordScores: Map<ItemId, Password.Score>,
    ): Boolean {
        if (filterState.selectedScores.isEmpty()) return true
        if (item.itemType != VaultItemType.Password) return true // non-password items - pass through

        val score = passwordScores[item.vaultItemId] ?: return false
        return score in filterState.selectedScores
    }

    private fun <I : LiteItem> sort(direction: SortDirection, items: List<I>): List<I> {
        val prepared = items.map { item ->
            item to item.name.split(CHUNK_REGEX)
        }

        val comp = compareByAlphanumeric<Pair<I, Parts>>(direction) { it.second }
        return prepared.sortedWith(comp).map { it.first }
    }

    private fun <T> compareByAlphanumeric(
        direction: SortDirection,
        selector: (T) -> Parts
    ): Comparator<T> = Comparator { a, b ->
        val cmp = compareParts(selector(a), selector(b))
        if (direction == SortDirection.Descending) -cmp else cmp
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