package de.davis.keygo.item.core.domain.model

import de.davis.keygo.core.domain.alias.ItemId

sealed interface Upsert {
    val name: FieldUpdate<String>
    val password: FieldUpdate<String>
    val totpSecret: FieldUpdate<String>
    val username: FieldUpdate<String>
    val website: FieldUpdate<String>
    val note: FieldUpdate<String>

    data class Create(
        override val name: FieldUpdate<String>,
        override val password: FieldUpdate<String>,
        override val totpSecret: FieldUpdate<String>,
        override val username: FieldUpdate<String>,
        override val website: FieldUpdate<String>,
        override val note: FieldUpdate<String>,
    ) : Upsert

    data class Update(
        val vaultId: ItemId,
        override val name: FieldUpdate<String> = keep(),
        override val password: FieldUpdate<String> = keep(),
        override val totpSecret: FieldUpdate<String> = keep(),
        override val username: FieldUpdate<String> = keep(),
        override val website: FieldUpdate<String> = keep(),
        override val note: FieldUpdate<String> = keep(),
    ) : Upsert
}