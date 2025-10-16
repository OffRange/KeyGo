package de.davis.keygo.core.item.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.SecretData

@Entity
internal data class VaultItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: ItemId,
    val name: String,
    val note: String?,
    @ColumnInfo(name = "encrypted_data")
    val encryptedData: SecretData<String>,
)