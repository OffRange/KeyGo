package de.davis.keygo.feature.list_screen.domain.model

import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.item.domain.model.Tag
import de.davis.keygo.core.item.generated.domain.model.VaultItemType

sealed interface FilterFacet<T : Any> {
    data object ItemTypes : FilterFacet<VaultItemType>
    data object Tags : FilterFacet<Tag>
    data object PasswordScores : FilterFacet<PasswordScore>

    data object Pinned : FilterFacet<Boolean>
}
