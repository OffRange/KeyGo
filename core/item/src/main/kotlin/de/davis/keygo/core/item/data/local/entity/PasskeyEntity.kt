package de.davis.keygo.core.item.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.SecretData

@Entity(
    tableName = "passkey",
    foreignKeys = [
        ForeignKey(
            entity = PasswordEntity::class,
            parentColumns = ["id"],
            childColumns = ["password_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["password_id"]), // Index for faster JOIN operations
        Index(value = ["rp"]), // Index for faster WHERE queries
    ],
)
internal class PasskeyEntity(
    @PrimaryKey
    @ColumnInfo(name = "credential_id")
    val credentialId: ByteArray,
    val rp: String,
    @ColumnInfo(name = "private_key")
    val privateKey: SecretData<String>,
    @ColumnInfo(name = "password_id")
    val passwordId: ItemId,
    val name: String,
    @ColumnInfo(name = "display_name")
    val displayName: String
)