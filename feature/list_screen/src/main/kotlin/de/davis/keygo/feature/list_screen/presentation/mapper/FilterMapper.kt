package de.davis.keygo.feature.list_screen.presentation.mapper

import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.item.domain.model.Tag
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.feature.list_screen.domain.model.FacetSelections
import de.davis.keygo.feature.list_screen.domain.model.FilterFacet
import de.davis.keygo.feature.list_screen.domain.model.FilterState
import de.davis.keygo.feature.list_screen.presentation.model.AvailableFilterOptions
import de.davis.keygo.feature.list_screen.presentation.model.FacetOptions
import de.davis.keygo.feature.list_screen.presentation.model.FilterBottomSheetState
import de.davis.keygo.feature.list_screen.presentation.model.ItemSectionState
import de.davis.keygo.feature.list_screen.presentation.model.PasswordSectionState

internal fun FilterState.toBottomSheetState(
    available: AvailableFilterOptions,
    restrictedItemType: VaultItemType?,
    retained: FacetSelections,
    isVisible: Boolean,
): FilterBottomSheetState {
    val itemTypeChips = available.itemTypes.chips(selections, retained)
    // A single type offers no choice, unless the user has already picked it.
    val showItemTypeChips = restrictedItemType == null &&
            (itemTypeChips.size > 1 || (selections + retained)[FilterFacet.ItemTypes].isNotEmpty())
    val onlyPinned = available.pinned.chips(selections, retained).singleOrNull()
    val tagChips = available.tags.chips(selections, retained)
    val scoreChips = available.passwordScores.chips(selections, retained)

    val effectiveItemTypes =
        restrictedItemType?.let { setOf(it) } ?: selections[FilterFacet.ItemTypes]
    val showLoginSection = scoreChips.isNotEmpty() && (
            effectiveItemTypes.isEmpty() ||
                    effectiveItemTypes.any(FilterFacet.PasswordScores::appliesTo)
            )

    val itemSection = ItemSectionState(
        onlyPinned = onlyPinned,
        itemTypeChips = if (showItemTypeChips) itemTypeChips else emptyList(),
        tagChips = tagChips,
    )

    return FilterBottomSheetState(
        sortDirection = sortDirection,
        itemSection = itemSection.takeIf {
            it.onlyPinned != null || it.itemTypeChips.isNotEmpty() || it.tagChips.isNotEmpty()
        },
        passwordSection = if (showLoginSection) PasswordSectionState(scoreChips) else null,
        isDefault = isDefault,
        isVisible = isVisible,
    )
}

internal fun FacetSelections.toAvailableFilterOptions(allTags: List<Tag>) = AvailableFilterOptions(
    itemTypes = options(FilterFacet.ItemTypes, VaultItemType.entries),
    tags = FacetOptions(
        facet = FilterFacet.Tags,
        order = allTags,
        available = allTags.filterTo(mutableSetOf()) { it in this[FilterFacet.Tags] },
    ),
    passwordScores = options(FilterFacet.PasswordScores, PasswordScore.entries.reversed()),
    pinned = options(FilterFacet.Pinned, listOf(true)),
)

private fun <T : Any> FacetSelections.options(facet: FilterFacet<T>, order: List<T>) =
    FacetOptions(facet, order, available = this[facet])
