package de.davis.keygo.core.data.local.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Password(
    @PrimaryKey(autoGenerate = true)
    val id: Long,
    @ColumnInfo("vault_id")
    val vaultId: Long,
    val username: String?,
    val website: String?
)
