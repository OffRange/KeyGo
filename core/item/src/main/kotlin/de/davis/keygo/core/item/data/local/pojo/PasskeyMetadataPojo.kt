package de.davis.keygo.core.item.data.local.pojo

import androidx.room.ColumnInfo

internal class PasskeyMetadataPojo(
    @ColumnInfo(name = "vault_name")
    val vaultName: String,
    @ColumnInfo(name = "password_username")
    val pwdUsername: String?,
    val name: String,
    @ColumnInfo(name = "display_name")
    val displayName: String,
    @ColumnInfo(name = "credential_id")
    val credentialId: ByteArray
)