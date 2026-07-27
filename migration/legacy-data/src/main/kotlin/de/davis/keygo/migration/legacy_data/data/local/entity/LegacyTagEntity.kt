package de.davis.keygo.migration.legacy_data.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "Tag", indices = [Index(value = ["name"], unique = true)])
internal data class LegacyTagEntity(
    val name: String,
    @PrimaryKey(autoGenerate = true) val tagId: Long = 0,
)
