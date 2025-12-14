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
    val credentialId: ByteArray,
    val rp: String,
    @ColumnInfo(name = "private_key")
    val privateKey: SecretData<String>,
    @ColumnInfo(name = "password_id")
    val passwordId: ItemId,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PasskeyEntity

        if (passwordId != other.passwordId) return false
        if (!credentialId.contentEquals(other.credentialId)) return false
        if (rp != other.rp) return false
        if (privateKey != other.privateKey) return false

        return true
    }

    override fun hashCode(): Int {
        var result = passwordId.hashCode()
        result = 31 * result + credentialId.contentHashCode()
        result = 31 * result + rp.hashCode()
        result = 31 * result + privateKey.hashCode()
        return result
    }
}