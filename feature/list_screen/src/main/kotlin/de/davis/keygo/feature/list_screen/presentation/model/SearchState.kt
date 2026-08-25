package de.davis.keygo.feature.list_screen.presentation.model

import androidx.compose.runtime.Stable
import de.davis.keygo.core.item.domain.model.lite.LiteItemSearchResult

@Stable
internal data class SearchState(
    val query: String = "",
    val results: List<LiteItemSearchResult> = emptyList(),
)