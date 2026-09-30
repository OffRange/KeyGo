package de.davis.keygo.feature.list_screen.presentation.model

import de.davis.keygo.feature.list_screen.domain.model.FacetSelections
import de.davis.keygo.feature.list_screen.domain.model.FilterFacet
import de.davis.keygo.feature.list_screen.domain.model.SortDirection

internal sealed interface FilterAction {
    data class SortDirectionChanged(val direction: SortDirection) : FilterAction

    data class Toggled<T : Any>(val facet: FilterFacet<T>, val value: T) : FilterAction {
        fun applyTo(selections: FacetSelections): FacetSelections =
            selections.toggle(facet, value)
    }

    data object ClearFilters : FilterAction
}
