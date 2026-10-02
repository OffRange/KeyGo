package de.davis.keygo.feature.list_screen.domain.usecase

import de.davis.keygo.core.item.domain.model.lite.LiteItemSearchResult
import de.davis.keygo.core.util.domain.comparator.NaturalOrderComparator
import org.koin.core.annotation.Single

@Single
class RankSearchResultsUseCase {

    operator fun invoke(
        query: String,
        results: List<LiteItemSearchResult>,
    ): List<LiteItemSearchResult> =
        if (query.isBlank()) results.sortedWith(ByName)
        else results.sortedWith(compareBy<LiteItemSearchResult> { rankOf(query, it) }.then(ByName))

    private fun rankOf(query: String, result: LiteItemSearchResult): Int = when {
        result.name.equals(query, ignoreCase = true) -> EXACT_NAME
        result.matchedName && result.name.startsWith(query, ignoreCase = true) -> NAME_PREFIX
        result.matchedName -> NAME
        result.matchedUsername -> USERNAME
        result.matchedTag -> TAG
        else -> NOTE
    }

    private companion object {
        const val EXACT_NAME = 0
        const val NAME_PREFIX = 1
        const val NAME = 2
        const val USERNAME = 3
        const val TAG = 4
        const val NOTE = 5

        val ByName = compareBy<LiteItemSearchResult, String>(NaturalOrderComparator) { it.name }
    }
}
