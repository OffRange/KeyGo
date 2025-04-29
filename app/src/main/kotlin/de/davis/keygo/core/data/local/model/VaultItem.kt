package de.davis.keygo.core.data.local.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import de.davis.keygo.core.domain.model.crypto.CryptographicData

@Entity
data class VaultItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long,
    val name: String,
    @ColumnInfo("encrypted_data")
    val encryptedData: CryptographicData,
    @ColumnInfo("short_note")
    val shortNote: String?
)