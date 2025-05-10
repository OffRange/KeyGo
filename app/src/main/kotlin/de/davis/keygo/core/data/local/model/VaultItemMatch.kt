package de.davis.keygo.core.data.local.model

import androidx.room.Embedded
import de.davis.keygo.generated.item.data.local.entity.VaultItemEntity

internal data class VaultItemMatch(
    @Embedded
    val item: VaultItemEntity,
    val matchedName: Boolean,
    val matchedNote: Boolean
)
