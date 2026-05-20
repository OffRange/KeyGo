package de.davis.keygo.feature.list_screen.domain.usecase

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.item.domain.model.lite.LiteItem
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.core.util.domain.usecase.SortUseCase
import de.davis.keygo.feature.list_screen.domain.model.FilterState
import de.davis.keygo.feature.list_screen.domain.model.SortDirection
import org.koin.core.annotation.Single

@Single
class FilterUseCase(
    private val sortUseCase: SortUseCase,
) {

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

        val (pinned, unpinned) = filtered.partition { it.pinned }
        return sort(filterState.sortDirection, pinned) +
                sort(filterState.sortDirection, unpinned)
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

    private fun <I : LiteItem> sort(direction: SortDirection, items: List<I>): List<I> =
        sortUseCase(items, ascending = direction == SortDirection.Ascending) { it.name }
}
