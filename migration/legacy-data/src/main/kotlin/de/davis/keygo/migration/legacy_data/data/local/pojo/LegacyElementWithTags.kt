package de.davis.keygo.migration.legacy_data.data.local.pojo

import androidx.room3.Embedded
import androidx.room3.Junction
import androidx.room3.Relation
import de.davis.keygo.migration.legacy_data.data.local.entity.LegacySecureElementEntity
import de.davis.keygo.migration.legacy_data.data.local.entity.LegacySecureElementTagCrossRef
import de.davis.keygo.migration.legacy_data.data.local.entity.LegacyTagEntity

internal data class LegacyElementWithTags(
    @Embedded val element: LegacySecureElementEntity,
    // room3 takes these as arrays; Room 2.x spelled the same thing in the singular.
    @Relation(
        parentColumns = ["id"],
        entityColumns = ["tagId"],
        associateBy = Junction(
            value = LegacySecureElementTagCrossRef::class,
            parentColumns = ["id"],
            entityColumns = ["tagId"],
        ),
    )
    val tags: List<LegacyTagEntity>,
)
