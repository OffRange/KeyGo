package de.davis.keygo.feature.list_screen.domain.usecase

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.item.domain.model.lite.LiteItem
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.core.util.domain.comparator.NaturalOrderComparator
import de.davis.keygo.feature.list_screen.domain.model.FilterState
import de.davis.keygo.feature.list_screen.domain.model.SortDirection
import org.koin.core.annotation.Single

@Single
class FilterUseCase {

    operator fun <I : LiteItem> invoke(
        filterState: FilterState,
        items: List<I>,
        passwordScores: Map<ItemId, PasswordScore>,
        tagMatchingIds: Set<ItemId>? = null,
    ): List<I> {
        val filtered = items.filter { item ->
            matchesItemType(filterState, item) &&
                    matchesScore(filterState, item, passwordScores) &&
                    matchesPinnedState(filterState, item) &&
                    matchesTags(tagMatchingIds, item)
        }

        return filtered.sortedWith(
            compareByDescending<LiteItem> { it.pinned }
                .thenBy(filterState.sortDirection.nameOrder) { it.name },
        )
    }

    private val SortDirection.nameOrder: Comparator<String>
        get() = when (this) {
            SortDirection.Ascending -> NaturalOrderComparator
            SortDirection.Descending -> NaturalOrderComparator.reversed()
        }

    private fun matchesPinnedState(filterState: FilterState, item: LiteItem): Boolean =
        !filterState.onlyPinned || item.pinned

    private fun matchesTags(tagMatchingIds: Set<ItemId>?, item: LiteItem): Boolean =
        tagMatchingIds == null || item.id in tagMatchingIds

    private fun matchesItemType(filterState: FilterState, item: LiteItem): Boolean =
        filterState.selectedItemTypes.isEmpty() || item.itemType in filterState.selectedItemTypes

    private fun matchesScore(
        filterState: FilterState,
        item: LiteItem,
        passwordScores: Map<ItemId, PasswordScore>,
    ): Boolean {
        if (filterState.selectedScores.isEmpty()) return true
        if (item.itemType != VaultItemType.Login) return true // non-password items - pass through

        val score = passwordScores[item.id] ?: return false
        return score in filterState.selectedScores
    }
}
