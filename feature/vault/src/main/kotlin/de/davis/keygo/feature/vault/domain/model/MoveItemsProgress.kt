package de.davis.keygo.feature.vault.domain.model

data class MoveItemsProgress(
    val movedCount: Int,
    val total: Int,
) {
    val fraction: Float = if (total <= 0) 1f else movedCount.toFloat() / total
}
