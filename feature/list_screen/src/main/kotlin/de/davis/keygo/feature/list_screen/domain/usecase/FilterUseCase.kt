package de.davis.keygo.feature.list_screen.domain.usecase

import de.davis.keygo.core.item.domain.model.lite.LiteItem
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.core.util.domain.usecase.SortUseCase
import de.davis.keygo.feature.list_screen.domain.model.FacetSelections
import de.davis.keygo.feature.list_screen.domain.model.FilterFacet
import de.davis.keygo.feature.list_screen.domain.model.FilterState
import de.davis.keygo.feature.list_screen.domain.model.ItemFacets
import de.davis.keygo.feature.list_screen.domain.model.SortDirection
import org.koin.core.annotation.Single

@Single
class FilterUseCase(
    private val sortUseCase: SortUseCase,
) {

    operator fun <I : LiteItem> invoke(
        filterState: FilterState,
        items: List<I>,
        facets: ItemFacets,
    ): List<I> {
        val selections = filterState.selections
        val filtered = items.filter { item ->
            selections.facets.all { facet ->
                item.matches(facet, selections, facets)
            }
        }

        val (pinned, unpinned) = filtered.partition { it.pinned }
        return sort(filterState.sortDirection, pinned) +
                sort(filterState.sortDirection, unpinned)
    }

    private fun LiteItem.matches(
        facet: FilterFacet<*>,
        selections: FacetSelections,
        facets: ItemFacets,
    ): Boolean = when (facet) {
        FilterFacet.ItemTypes -> itemType in selections[FilterFacet.ItemTypes]
        FilterFacet.Tags -> facets.tagsByItem[id].orEmpty()
            .any { it in selections[FilterFacet.Tags] }

        FilterFacet.Pinned -> pinned in selections[FilterFacet.Pinned]
        // Only logins carry a password, so every other item passes through.
        FilterFacet.PasswordScores -> itemType != VaultItemType.Login ||
                facets.passwordScores[id]?.let { it in selections[FilterFacet.PasswordScores] } == true
    }

    private fun <I : LiteItem> sort(direction: SortDirection, items: List<I>): List<I> =
        sortUseCase(items, ascending = direction == SortDirection.Ascending) { it.name }
}
