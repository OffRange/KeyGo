package de.davis.keygo.migration.legacy_data.domain.model

/** A single v1 row, decrypted and parsed, with its user tags already filtered. */
internal data class LegacyItem(
    val legacyId: Long,
    val title: String,
    val favorite: Boolean,
    val createdAt: Long?,
    val modifiedAt: Long?,
    val tags: Set<String>,
    val detail: LegacyDetail,
)

/** Marks v1's type-discriminator tags, which are dropped rather than carried across as user tags. */
internal const val LEGACY_TAG_PREFIX = "elementType:"
