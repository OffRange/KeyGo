package de.davis.keygo.feature.list_screen.presentation.mapper

import androidx.compose.ui.util.fastMapTo
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.item.domain.model.Tag
import de.davis.keygo.core.item.domain.model.lite.LiteItem
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.feature.list_screen.domain.model.FilterState
import de.davis.keygo.feature.list_screen.presentation.model.AvailableFilterOptions
import de.davis.keygo.feature.list_screen.presentation.model.FilterBottomSheetState
import de.davis.keygo.feature.list_screen.presentation.model.FilterChipState
import de.davis.keygo.feature.list_screen.presentation.model.ItemSectionState
import de.davis.keygo.feature.list_screen.presentation.model.PasswordSectionState

internal fun FilterState.toBottomSheetState(
    available: AvailableFilterOptions,
    restrictedItemType: VaultItemType?,
): FilterBottomSheetState {
    val showItemTypeChips = restrictedItemType == null && available.itemTypes.size > 1
    val showItemSection =
        showItemTypeChips || available.labels.isNotEmpty() || available.hasPinnedItems

    val effectiveItemTypes = restrictedItemType?.let { setOf(it) } ?: selectedItemTypes
    val showLoginSection = available.hasPasswordItems &&
            (effectiveItemTypes.isEmpty() || VaultItemType.Login in effectiveItemTypes)

    return FilterBottomSheetState(
        sortDirection = sortDirection,
        itemSection = if (showItemSection) ItemSectionState(
            showPinnedSwitch = available.hasPinnedItems,
            onlyPinnedChecked = onlyPinned,
            itemTypeChips = if (showItemTypeChips) available.itemTypes.map { type ->
                FilterChipState(value = type, selected = type in selectedItemTypes)
            } else emptyList(),
            labelChips = available.labels.map { label ->
                FilterChipState(value = label, selected = label in selectedLabels)
            },
        ) else null,
        passwordSection = if (showLoginSection) PasswordSectionState(
            passwordScoreChips = available.passwordScores
                .sortedByDescending { it.ordinal }
                .map { score ->
                    FilterChipState(value = score, selected = score in selectedScores)
                },
        ) else null,
        isDefault = isDefault,
    )
}

internal fun List<LiteItem>.toAvailableFilterOptions(
    passwordScores: Map<ItemId, PasswordScore>,
    allTags: Set<Tag>,
): AvailableFilterOptions {
    val itemTypes = fastMapTo(mutableSetOf()) { it.itemType }
    val hasLoginItems = VaultItemType.Login in itemTypes

    val itemIds = if (hasLoginItems) fastMapTo(mutableSetOf()) { it.id }
    else emptySet()

    val scores = if (hasLoginItems)
        passwordScores.filterKeys { it in itemIds }.values.toSet()
    else emptySet()

    return AvailableFilterOptions(
        itemTypes = itemTypes,
        hasPasswordItems = hasLoginItems,
        passwordScores = scores,
        labels = allTags,
        hasPinnedItems = any { it.pinned },
    )
}
