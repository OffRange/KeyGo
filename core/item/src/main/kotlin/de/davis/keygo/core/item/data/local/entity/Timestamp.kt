package de.davis.keygo.core.item.data.local.entity

import androidx.room.ColumnInfo

internal data class Timestamp(
    @ColumnInfo("created_at")
    val createdAt: Long,
    @ColumnInfo("modified_at")
    val modifiedAt: Long?,
)
