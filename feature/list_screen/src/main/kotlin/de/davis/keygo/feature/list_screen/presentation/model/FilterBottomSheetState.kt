package de.davis.keygo.feature.list_screen.presentation.model

import androidx.compose.runtime.Immutable
import de.davis.keygo.core.item.domain.model.Password
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.feature.list_screen.domain.model.SortDirection

@Immutable
internal data class FilterBottomSheetState(
    val sortDirection: SortDirection = SortDirection.Ascending,
    val itemSection: ItemSectionState? = null,
    val passwordSection: PasswordSectionState? = null,
    val isDefault: Boolean = true,
)

@Immutable
internal data class ItemSectionState(
    val showPinnedSwitch: Boolean,
    val onlyPinnedChecked: Boolean,
    val itemTypeChips: List<FilterChipState<VaultItemType>>,
    val labelChips: List<FilterChipState<String>>,
)

@Immutable
internal data class PasswordSectionState(
    val scoreChips: List<FilterChipState<Password.Score>>,
)

@Immutable
internal data class FilterChipState<out T>(
    val value: T,
    val selected: Boolean,
)
