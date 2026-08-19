package de.davis.keygo.legacy_migration.data.mapper

import de.davis.keygo.legacy_migration.data.local.pojo.LegacyElementWithTags
import de.davis.keygo.legacy_migration.domain.model.LEGACY_TAG_PREFIX
import de.davis.keygo.legacy_migration.domain.model.LegacyDetail
import de.davis.keygo.legacy_migration.domain.model.LegacyItem

/**
 * Turns a decrypted, parsed row into a [LegacyItem].
 *
 * v1's `elementType:` tags were type discriminators rather than user data. v1's own UI hid them via
 * `onlyCustoms()`, and v2 carries the same information in `item.item_type`, so they are dropped.
 *
 * `createdAt` is carried across exactly as it was found, null included. Schema version 1 had no
 * timestamp columns, so a row that came up from it genuinely has none, and inventing one here would
 * hide that from the fallback applied when the item is converted.
 */
internal fun LegacyElementWithTags.toLegacyItem(detail: LegacyDetail): LegacyItem = LegacyItem(
    legacyId = element.id,
    title = element.title,
    favorite = element.favorite,
    createdAt = element.timestamps.createdAt,
    modifiedAt = element.timestamps.modifiedAt,
    tags = tags.asSequence()
        .map { it.name }
        .filterNot { it.startsWith(LEGACY_TAG_PREFIX) }
        .toSet(),
    detail = detail,
)
