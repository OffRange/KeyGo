package de.davis.keygo.legacy_migration.data.local.entity

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(tableName = "Tag", indices = [Index(value = ["name"], unique = true)])
internal data class LegacyTagEntity(
    val name: String,
    @PrimaryKey(autoGenerate = true) val tagId: Long = 0,
)
