package de.davis.keygo.legacy_migration.data.local.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index

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
