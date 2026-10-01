package de.davis.keygo.core.item.data.local.pojo

import androidx.room3.ColumnInfo
import de.davis.keygo.core.item.domain.alias.ItemId

internal data class LoginCredentialsProjection(
    val id: ItemId,
    @ColumnInfo(name = "has_password")
    val hasPassword: Boolean,
    @ColumnInfo(name = "has_passkey")
    val hasPasskey: Boolean,
    @ColumnInfo(name = "has_totp")
    val hasTotp: Boolean,
)
