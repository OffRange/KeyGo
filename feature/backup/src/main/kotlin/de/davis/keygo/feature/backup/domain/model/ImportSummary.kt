package de.davis.keygo.feature.backup.domain.model

data class ImportSummary(
    val imported: Int,
    val skipped: Int,
    val failed: Int,
    val vaultsCreated: Int,
)
