package de.davis.passwordmanager.database.entities

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import de.davis.passwordmanager.database.ElementType
import de.davis.passwordmanager.database.entities.details.ElementDetail

@Entity(tableName = "SecureElement")
data class SecureElementEntity @JvmOverloads constructor(
    val title: String,
    @ColumnInfo(name = "data") val detail: ElementDetail,
    val favorite: Boolean = false,
    @Embedded val timestamps: Timestamps = Timestamps.CURRENT,
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
) {
    var type: ElementType = detail.elementType
}