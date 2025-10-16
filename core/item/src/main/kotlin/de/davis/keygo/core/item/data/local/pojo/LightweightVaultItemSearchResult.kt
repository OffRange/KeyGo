package de.davis.keygo.core.item.data.local.pojo

internal data class LightweightVaultItemSearchResult(
    val id: Long,
    val name: String,
    val matchedName: Boolean,
    val matchedNote: Boolean
)
