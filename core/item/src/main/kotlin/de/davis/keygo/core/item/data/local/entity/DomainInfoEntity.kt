package de.davis.keygo.core.item.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    primaryKeys = ["password_id", "value"],
    foreignKeys = [
        ForeignKey(
            entity = PasswordEntity::class,
            parentColumns = ["id"],
            childColumns = ["password_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index("eTLD1"),
    ]
)
internal data class DomainInfoEntity(
    @ColumnInfo("password_id")
    val passwordId: Long,
    val value: String,
    val eTLD1: String?,
)