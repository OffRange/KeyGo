package de.davis.keygo.feature.list_screen.domain.model

import de.davis.keygo.core.item.domain.model.Password
import de.davis.keygo.core.item.generated.domain.model.VaultItemType

data class FilterState(
    val sortDirection: SortDirection = SortDirection.Ascending,
    val selectedScores: Set<Password.Score> = emptySet(),
    val selectedItemTypes: Set<VaultItemType> = emptySet(),
    val selectedLabels: Set<String> = emptySet(),
) {

    val isDefault: Boolean
        get() = this == Default

    companion object {
        val Default = FilterState()
    }
}
