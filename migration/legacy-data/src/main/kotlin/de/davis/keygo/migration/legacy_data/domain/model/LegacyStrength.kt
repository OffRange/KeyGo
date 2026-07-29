package de.davis.keygo.migration.legacy_data.domain.model

/**
 * v1's `Strength`, in v1's declaration order. The ordinal is load-bearing: the older JSON form
 * stored it as `{"type": ordinal}`.
 */
internal enum class LegacyStrength {
    RIDICULOUS,
    WEAK,
    MODERATE,
    STRONG,
    VERY_STRONG,
}
