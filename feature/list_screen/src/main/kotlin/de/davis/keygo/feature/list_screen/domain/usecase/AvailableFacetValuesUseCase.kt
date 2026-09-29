package de.davis.keygo.feature.list_screen.domain.usecase

import de.davis.keygo.core.item.domain.model.lite.LiteItem
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.feature.list_screen.domain.model.FacetSelections
import de.davis.keygo.feature.list_screen.domain.model.FilterFacet
import de.davis.keygo.feature.list_screen.domain.model.ItemFacets
import org.koin.core.annotation.Single

@Single
class AvailableFacetValuesUseCase {

    operator fun invoke(items: List<LiteItem>, facets: ItemFacets): FacetSelections {
        val itemTypes = items.mapTo(mutableSetOf()) { it.itemType }

        val tags = buildSet {
            items.forEach { item -> facets.tagsByItem[item.id]?.let(::addAll) }
        }

        // Only logins carry a password, so a score keyed to any other item type must not surface.
        val loginIds = items.filter { it.itemType == VaultItemType.Login }
            .mapTo(mutableSetOf()) { it.id }
        val scores = facets.passwordScores.filterKeys { it in loginIds }.values.toSet()

        val pinned = if (items.any { it.pinned }) setOf(true) else emptySet()

        return FacetSelections.None
            .with(FilterFacet.ItemTypes, itemTypes)
            .with(FilterFacet.Tags, tags)
            .with(FilterFacet.PasswordScores, scores)
            .with(FilterFacet.Pinned, pinned)
    }
}
