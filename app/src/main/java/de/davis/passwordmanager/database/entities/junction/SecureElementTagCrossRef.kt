package de.davis.passwordmanager.database.entities.junction

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.Companion.CASCADE
import de.davis.passwordmanager.database.entities.SecureElementEntity
import de.davis.passwordmanager.database.entities.Tag

@Entity(
    primaryKeys = ["id", "tagId"], foreignKeys = [
        ForeignKey(
            entity = SecureElementEntity::class,
            parentColumns = ["id"],
            childColumns = ["id"],
            onDelete = CASCADE,
            onUpdate = CASCADE
        ),
        ForeignKey(
            entity = Tag::class,
            parentColumns = ["tagId"],
            childColumns = ["tagId"],
            onDelete = CASCADE,
            onUpdate = CASCADE
        )
    ]
)
data class SecureElementTagCrossRef(
    @ColumnInfo(index = true) val id: Long,
    @ColumnInfo(index = true) val tagId: Long
)