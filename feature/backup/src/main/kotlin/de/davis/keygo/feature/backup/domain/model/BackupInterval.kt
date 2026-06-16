package de.davis.keygo.feature.backup.domain.model

data class BackupInterval(
    val count: Int,
    val unit: IntervalUnit,
)

enum class IntervalUnit {
    Days,
    Weeks,
}
