package de.davis.keygo.core.data.local.model

import androidx.room.Embedded

data class VaultItemMatch(
    @Embedded
    val item: VaultItem,
    val matchedName: Boolean,
    val matchedNote: Boolean
)
