package de.davis.keygo.feature.list_screen.presentation.mapper

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
    retained: FacetSelections = FacetSelections.None,
    isVisible: Boolean = false,
): FilterBottomSheetState {
    val itemTypeChips = available.itemTypes.chips(selections, retained)
    // A single type offers no choice, unless the user has already picked it.
    val showItemTypeChips = restrictedItemType == null &&
            (itemTypeChips.size > 1 || (selections + retained)[FilterFacet.ItemTypes].isNotEmpty())
    val onlyPinned = available.pinned.chips(selections, retained).singleOrNull()
    val tagChips = available.tags.chips(selections, retained)
    val scoreChips = available.passwordScores.chips(selections, retained)

    val effectiveItemTypes = restrictedItemType?.let { setOf(it) } ?: this[FilterFacet.ItemTypes]
    val showLoginSection = scoreChips.isNotEmpty() &&
            (effectiveItemTypes.isEmpty() || VaultItemType.Login in effectiveItemTypes)

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

internal fun FacetSelections.toAvailableFilterOptions(allTags: List<Tag>): AvailableFilterOptions {
    val defaults = AvailableFilterOptions()

    return AvailableFilterOptions(
        itemTypes = defaults.itemTypes.copy(available = this[FilterFacet.ItemTypes]),
        tags = FacetOptions(
            facet = FilterFacet.Tags,
            order = allTags,
            available = allTags.filterTo(mutableSetOf()) { it in this[FilterFacet.Tags] },
        ),
        passwordScores = defaults.passwordScores.copy(available = this[FilterFacet.PasswordScores]),
        pinned = defaults.pinned.copy(available = this[FilterFacet.Pinned]),
    )
}
