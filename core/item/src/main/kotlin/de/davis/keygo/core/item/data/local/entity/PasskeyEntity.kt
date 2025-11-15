package de.davis.keygo.core.item.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.SecretData

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = PasswordEntity::class,
            parentColumns = ["id"],
            childColumns = ["password_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["password_id"])
    ],
)
internal data class PasskeyEntity(
    @PrimaryKey
    @ColumnInfo(name = "credential_id")
    val credentialId: String,
    @ColumnInfo(name = "private_key")
    val privateKey: SecretData<String>,
    @ColumnInfo(name = "password_id")
    val passwordId: ItemId,
)