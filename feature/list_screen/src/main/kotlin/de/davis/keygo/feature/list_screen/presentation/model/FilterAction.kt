package de.davis.keygo.feature.list_screen.presentation.model

import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.item.domain.model.Tag
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.feature.list_screen.domain.model.SortDirection

internal sealed interface FilterAction {
    data class SortDirectionChanged(val direction: SortDirection) : FilterAction
    data class ItemTypeToggled(val itemType: VaultItemType) : FilterAction
    data class TagToggled(val tag: Tag) : FilterAction
    data class ScoreToggled(val passwordScore: PasswordScore) : FilterAction
    data object ShowOnlyPinnedToggled : FilterAction
    data object ClearFilters : FilterAction
}
