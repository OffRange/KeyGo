package de.davis.keygo.feature.list_screen.domain.usecase

import de.davis.keygo.core.item.domain.model.lite.LiteItem
import de.davis.keygo.feature.list_screen.domain.model.FacetSelections
import de.davis.keygo.feature.list_screen.domain.model.FilterFacet
import de.davis.keygo.feature.list_screen.domain.model.ItemAttributes
import org.koin.core.annotation.Single

@Single
class AvailableFacetValuesUseCase {

    operator fun invoke(items: List<LiteItem>, attributes: ItemAttributes): FacetSelections =
        FilterFacet.entries.fold(FacetSelections.None) { available, facet ->
            available.withValuesOf(facet, items, attributes)
        }

    private fun <T : Any> FacetSelections.withValuesOf(
        facet: FilterFacet<T>,
        items: List<LiteItem>,
        attributes: ItemAttributes,
    ): FacetSelections = with(
        facet,
        items.filter { facet.appliesTo(it.itemType) }
            .flatMapTo(mutableSetOf()) { facet.valuesFor(it, attributes) },
    )
}
