package de.davis.keygo.feature.list_screen.domain.usecase

import de.davis.keygo.core.item.domain.model.lite.LiteItem
import de.davis.keygo.core.item.domain.repository.ItemRepository
import de.davis.keygo.core.item.domain.repository.LoginRepository
import de.davis.keygo.feature.list_screen.domain.model.FilterResult
import de.davis.keygo.feature.list_screen.domain.model.FilterState
import de.davis.keygo.feature.list_screen.domain.model.ItemFacets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.core.annotation.Single

@Single
class ObserveFilterResultUseCase(
    private val itemRepository: ItemRepository,
    private val loginRepository: LoginRepository,
    private val filterUseCase: FilterUseCase,
    private val availableFacetValues: AvailableFacetValuesUseCase,
) {

    operator fun <I : LiteItem> invoke(
        items: Flow<List<I>>,
        filterState: Flow<FilterState>,
    ): Flow<FilterResult<I>> {
        val facets = combine(
            loginRepository.observePasswordScores(),
            itemRepository.observeTagsByItem(),
            ::ItemFacets,
        )
        return combine(items, filterState, facets) { items, filter, facets ->
            val filtered = filterUseCase(filter, items, facets)
            FilterResult(
                items = filtered,
                available = availableFacetValues(items, facets),
                isEmptyBecauseOfFilter = filtered.isEmpty() && items.isNotEmpty(),
            )
        }.distinctUntilChanged()
    }
}
