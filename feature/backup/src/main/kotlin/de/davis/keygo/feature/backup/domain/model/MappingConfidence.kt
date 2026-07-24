package de.davis.keygo.feature.backup.domain.model

/** How strongly the CSV analyzer believes a column's auto-detected type is correct. */
enum class MappingConfidence {
    High,
    Medium,
    Low,
}
