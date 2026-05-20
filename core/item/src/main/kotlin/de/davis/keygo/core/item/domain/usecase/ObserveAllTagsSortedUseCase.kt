package de.davis.keygo.core.item.domain.usecase

import de.davis.keygo.core.item.domain.model.Tag
import de.davis.keygo.core.item.domain.repository.ItemRepository
import de.davis.keygo.core.util.domain.usecase.SortUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

@Single
class ObserveAllTagsSortedUseCase(
    private val itemRepository: ItemRepository,
    private val sortUseCase: SortUseCase,
) {
    operator fun invoke(): Flow<List<Tag>> =
        itemRepository.observeAllTags().map { tags -> sortUseCase(tags) { it.display } }
}
