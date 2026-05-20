package de.davis.keygo.feature.list_screen.presentation.model

import androidx.compose.runtime.Immutable
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.item.domain.model.Tag
import de.davis.keygo.core.item.generated.domain.model.VaultItemType

@Immutable
internal data class AvailableFilterOptions(
    val passwordScores: Set<PasswordScore> = emptySet(),
    val itemTypes: Set<VaultItemType> = emptySet(),
    val tags: Set<Tag> = emptySet(),
    val hasPasswordItems: Boolean = false,
    val hasPinnedItems: Boolean = false
)
