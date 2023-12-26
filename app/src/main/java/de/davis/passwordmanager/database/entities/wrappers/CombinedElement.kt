package de.davis.passwordmanager.database.entities.wrappers

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import de.davis.passwordmanager.database.entities.SecureElementEntity
import de.davis.passwordmanager.database.entities.Tag
import de.davis.passwordmanager.database.entities.junction.SecureElementTagCrossRef

data class CombinedElement(
    @Embedded val secureElementEntity: SecureElementEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "tagId",
        associateBy = Junction(SecureElementTagCrossRef::class)
    )
    val tags: List<Tag>
)
