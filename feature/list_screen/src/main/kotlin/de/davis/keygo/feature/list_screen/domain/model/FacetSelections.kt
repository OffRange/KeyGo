package de.davis.keygo.feature.list_screen.domain.model

@JvmInline
value class FacetSelections private constructor(
    private val byFacet: Map<FilterFacet<*>, Set<Any>>,
) {

    val facets: Set<FilterFacet<*>> get() = byFacet.keys

    // Every write goes through with(), which stores a facet's values only under that facet.
    @Suppress("UNCHECKED_CAST")
    operator fun <T : Any> get(facet: FilterFacet<T>): Set<T> =
        byFacet[facet].orEmpty() as Set<T>

    // An empty set is dropped rather than stored, so a cleared facet compares equal to one that
    // was never touched.
    fun <T : Any> with(facet: FilterFacet<T>, values: Set<T>): FacetSelections =
        FacetSelections(if (values.isEmpty()) byFacet - facet else byFacet + (facet to values))

    fun <T : Any> toggle(facet: FilterFacet<T>, value: T): FacetSelections {
        val current = get(facet)
        return with(facet, if (value in current) current - value else current + value)
    }

    operator fun plus(other: FacetSelections): FacetSelections =
        FacetSelections(
            (facets + other.facets).associateWith { byFacet[it].orEmpty() + other.byFacet[it].orEmpty() },
        )

    companion object {
        val None = FacetSelections(emptyMap())
    }
}
