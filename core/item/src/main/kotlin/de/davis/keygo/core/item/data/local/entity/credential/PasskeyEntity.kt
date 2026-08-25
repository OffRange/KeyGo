package de.davis.keygo.core.item.data.local.entity.credential

import androidx.room3.ColumnInfo
import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import de.davis.keygo.core.item.data.local.entity.LoginEntity
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.EncryptedPayload

@Entity(
    tableName = "passkey",
    foreignKeys = [
        ForeignKey(
            entity = LoginEntity::class,
            parentColumns = ["id"],
            childColumns = ["login_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["login_id"]), // Index for faster JOIN operations
        Index(value = ["rp"]), // Index for faster WHERE queries
    ],
)
internal class PasskeyEntity(
    @PrimaryKey
    @ColumnInfo(name = "credential_id")
    val credentialId: ByteArray,
    @ColumnInfo(name = "login_id")
    val loginId: ItemId,
    val rp: String,
    @Embedded(prefix = "private_key_")
    val privateKey: EncryptedPayload,
    val name: String,
    @ColumnInfo(name = "display_name")
    val displayName: String
)