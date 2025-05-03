package de.davis.keygo.core.data.local.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = VaultItem::class,
            parentColumns = ["id"],
            childColumns = ["vault_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["vault_id"], unique = true)
    ]
)
data class Password(
    @PrimaryKey(autoGenerate = true)
    val id: Long,
    @ColumnInfo("vault_id")
    val vaultId: Long,
    val username: String?,
    val website: String?
)
