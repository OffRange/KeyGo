package de.davis.keygo.core.item.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tag",
    indices = [Index(value = ["normalized"], unique = true)],
)
internal data class TagEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val value: String,
    val normalized: String,
)
