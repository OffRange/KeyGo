package de.davis.keygo.feature.list_screen.presentation.model

import androidx.compose.runtime.Immutable
import de.davis.keygo.core.item.domain.model.CardExpiryStatus
import de.davis.keygo.core.item.domain.model.CredentialType
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.item.domain.model.Tag
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.feature.list_screen.domain.model.FacetSelections
import de.davis.keygo.feature.list_screen.domain.model.FilterFacet

@Immutable
internal data class AvailableFilterOptions(
    val itemTypes: FacetOptions<VaultItemType>,
    val tags: FacetOptions<Tag>,
    val passwordScores: FacetOptions<PasswordScore>,
    val credentials: FacetOptions<CredentialType>,
    val expiryStatuses: FacetOptions<CardExpiryStatus>,
    val pinned: FacetOptions<Boolean>,
)

@Immutable
internal data class FacetOptions<T : Any>(
    val facet: FilterFacet<T>,
    val order: List<T>,
    val available: Set<T>,
) {

    fun chips(selections: FacetSelections, retained: FacetSelections): List<FilterChipState<T>> {
        val selected = selections[facet]
        val shown = available + selected + retained[facet]
        return (order + shown).distinct()
            .filter { it in shown } // drops the ordered values that shouldn't be shown
            .map { FilterChipState(value = it, selected = it in selected) }
    }
}
