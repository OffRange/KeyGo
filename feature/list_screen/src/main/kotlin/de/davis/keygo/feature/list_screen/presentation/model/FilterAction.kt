package de.davis.keygo.feature.list_screen.presentation.model

import de.davis.keygo.core.item.domain.model.Password
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.feature.list_screen.domain.model.SortDirection

internal sealed interface FilterAction {
    data class SortDirectionChanged(val direction: SortDirection) : FilterAction
    data class ItemTypeToggled(val itemType: VaultItemType) : FilterAction
    data class LabelToggled(val label: String) : FilterAction
    data class ScoreToggled(val score: Password.Score) : FilterAction
    data object ClearFilters : FilterAction
}
