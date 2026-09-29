package de.davis.keygo.feature.list_screen.presentation.model

import androidx.compose.runtime.Immutable
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.item.domain.model.Tag
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.feature.list_screen.domain.model.FacetSelections
import de.davis.keygo.feature.list_screen.domain.model.FilterFacet

@Immutable
internal data class AvailableFilterOptions(
    val itemTypes: FacetOptions<VaultItemType> =
        FacetOptions(FilterFacet.ItemTypes, VaultItemType.entries),
    val tags: FacetOptions<Tag> = FacetOptions(FilterFacet.Tags, emptyList()),
    val passwordScores: FacetOptions<PasswordScore> =
        FacetOptions(FilterFacet.PasswordScores, PasswordScore.entries.reversed()),
    val pinned: FacetOptions<Boolean> = FacetOptions(FilterFacet.Pinned, listOf(true)),
)

@Immutable
internal data class FacetOptions<T : Any>(
    val facet: FilterFacet<T>,
    val order: List<T>,
    val available: Set<T> = emptySet(),
) {

    fun chips(
        selections: FacetSelections,
        retained: FacetSelections = FacetSelections.None,
    ): List<FilterChipState<T>> {
        val selected = selections[facet]
        val shown = available + selected + retained[facet]
        return (order + shown).distinct()
            .filter { it in shown }
            .map { FilterChipState(value = it, selected = it in selected) }
    }
}
