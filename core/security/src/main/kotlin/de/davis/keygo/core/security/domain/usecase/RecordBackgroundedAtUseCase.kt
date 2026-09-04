package de.davis.keygo.core.security.domain.usecase

import de.davis.keygo.core.security.domain.repository.LockInfoRepository
import de.davis.keygo.core.security.domain.time.ElapsedTimeProvider
import org.koin.core.annotation.Single

@Single
class RecordBackgroundedAtUseCase(
    private val elapsedTimeProvider: ElapsedTimeProvider,
    private val lockInfoRepository: LockInfoRepository,
) {

    suspend operator fun invoke() {
        val currentTime = elapsedTimeProvider.elapsedTime()
        lockInfoRepository.setBackgroundedAt(currentTime)
    }
}