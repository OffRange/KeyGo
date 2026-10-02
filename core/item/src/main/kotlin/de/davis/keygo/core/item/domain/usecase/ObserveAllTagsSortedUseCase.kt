package de.davis.keygo.core.item.domain.usecase

import de.davis.keygo.core.item.domain.model.Tag
import de.davis.keygo.core.item.domain.repository.ItemRepository
import de.davis.keygo.core.util.domain.comparator.sortedNaturallyBy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

@Single
class ObserveAllTagsSortedUseCase(
    private val itemRepository: ItemRepository,
) {
    operator fun invoke(): Flow<List<Tag>> =
        itemRepository.observeAllTags().map { tags -> tags.sortedNaturallyBy { it.display } }
}
