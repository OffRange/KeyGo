package de.davis.keygo.feature.list_screen.domain.model

import de.davis.keygo.core.item.domain.model.lite.LiteItem

data class FilterResult<I : LiteItem>(
    val items: List<I>,
    val available: FacetSelections,
    val isEmptyBecauseOfFilter: Boolean,
)
