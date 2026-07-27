package de.davis.keygo.migration.legacy_data.data.local.pojo

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import de.davis.keygo.migration.legacy_data.data.local.entity.LegacySecureElementEntity
import de.davis.keygo.migration.legacy_data.data.local.entity.LegacySecureElementTagCrossRef
import de.davis.keygo.migration.legacy_data.data.local.entity.LegacyTagEntity

internal data class LegacyElementWithTags(
    @Embedded val element: LegacySecureElementEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "tagId",
        associateBy = Junction(
            value = LegacySecureElementTagCrossRef::class,
            parentColumn = "id",
            entityColumn = "tagId",
        ),
    )
    val tags: List<LegacyTagEntity>,
)
