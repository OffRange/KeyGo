package de.davis.keygo.item.core.domain.model

import de.davis.keygo.core.domain.alias.ItemId

sealed interface Upsert {
    val name: String?
    val password: String?
    val username: String?
    val website: String?
    val note: String?

    data class Create(
        override val name: String,
        override val password: String,
        override val username: String? = null,
        override val website: String? = null,
        override val note: String? = null,
    ) : Upsert

    data class Update(
        val vaultId: ItemId,
        override val name: String? = null,
        override val password: String? = null,
        override val username: String? = null,
        override val website: String? = null,
        override val note: String? = null,
    ) : Upsert
}