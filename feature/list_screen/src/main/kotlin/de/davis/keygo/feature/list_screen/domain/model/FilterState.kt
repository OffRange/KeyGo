package de.davis.keygo.feature.list_screen.domain.model

data class FilterState(
    val sortDirection: SortDirection = SortDirection.Ascending,
    val selections: FacetSelections = FacetSelections.None,
) {

    val isDefault: Boolean
        get() = this == Default

    operator fun <T : Any> get(facet: FilterFacet<T>): Set<T> = selections[facet]

    fun <T : Any> with(facet: FilterFacet<T>, values: Set<T>): FilterState =
        copy(selections = selections.with(facet, values))

    companion object {
        val Default = FilterState()
    }
}
