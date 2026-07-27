package de.davis.keygo.migration.legacy_data.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "SecureElementTagCrossRef",
    primaryKeys = ["id", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = LegacySecureElementEntity::class,
            parentColumns = ["id"],
            childColumns = ["id"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LegacyTagEntity::class,
            parentColumns = ["tagId"],
            childColumns = ["tagId"],
            onUpdate = ForeignKey.CASCADE,
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("id"), Index("tagId")],
)
internal data class LegacySecureElementTagCrossRef(
    val id: Long,
    val tagId: Long,
)
