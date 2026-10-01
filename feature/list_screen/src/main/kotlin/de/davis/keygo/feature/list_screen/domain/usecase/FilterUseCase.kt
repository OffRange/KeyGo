package de.davis.keygo.feature.list_screen.domain.usecase

import de.davis.keygo.core.item.domain.model.lite.LiteItem
import de.davis.keygo.core.util.domain.usecase.SortUseCase
import de.davis.keygo.feature.list_screen.domain.model.FacetSelections
import de.davis.keygo.feature.list_screen.domain.model.FilterFacet
import de.davis.keygo.feature.list_screen.domain.model.FilterState
import de.davis.keygo.feature.list_screen.domain.model.ItemAttributes
import de.davis.keygo.feature.list_screen.domain.model.SortDirection
import org.koin.core.annotation.Single

@Single
class FilterUseCase(
    private val sortUseCase: SortUseCase,
) {

    operator fun <I : LiteItem> invoke(
        filterState: FilterState,
        items: List<I>,
        attributes: ItemAttributes,
    ): List<I> {
        val selections = filterState.selections
        val filtered = items.filter { item ->
            selections.facets
                .filter { it.appliesTo(item.itemType) }
                .all { facet -> facet.matches(item, selections, attributes) }
        }

        val (pinned, unpinned) = filtered.partition { it.pinned }
        return sort(filterState.sortDirection, pinned) +
                sort(filterState.sortDirection, unpinned)
    }

    private fun <T : Any> FilterFacet<T>.matches(
        item: LiteItem,
        selections: FacetSelections,
        attributes: ItemAttributes,
    ): Boolean = valuesFor(item, attributes).any { it in selections[this] }

    private fun <I : LiteItem> sort(direction: SortDirection, items: List<I>): List<I> =
        sortUseCase(items, ascending = direction == SortDirection.Ascending) { it.name }
}
