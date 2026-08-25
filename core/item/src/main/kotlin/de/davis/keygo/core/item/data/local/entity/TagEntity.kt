package de.davis.keygo.core.item.data.local.entity

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

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
