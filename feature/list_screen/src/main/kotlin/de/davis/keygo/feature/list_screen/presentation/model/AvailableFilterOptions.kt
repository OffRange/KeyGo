package de.davis.keygo.feature.list_screen.presentation.model

import androidx.compose.runtime.Immutable
import de.davis.keygo.core.item.domain.model.Login
import de.davis.keygo.core.item.generated.domain.model.VaultItemType

@Immutable
internal data class AvailableFilterOptions(
    val scores: Set<Login.Score> = emptySet(),
    val itemTypes: Set<VaultItemType> = emptySet(),
    val labels: Set<String> = emptySet(),
    val hasPasswordItems: Boolean = false,
    val hasPinnedItems: Boolean = false
)
