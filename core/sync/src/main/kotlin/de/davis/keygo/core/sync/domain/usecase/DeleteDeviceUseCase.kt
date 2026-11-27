package de.davis.keygo.core.sync.domain.usecase

import de.davis.keygo.core.sync.domain.model.SyncError
import de.davis.keygo.core.sync.domain.repository.SyncRepository
import de.davis.keygo.core.sync.domain.stepup.StepUpGate
import de.davis.keygo.core.util.Result
import org.koin.core.annotation.Factory
import java.util.UUID

@Factory
class DeleteDeviceUseCase(
    private val syncRepository: SyncRepository,
    private val stepUpGate: StepUpGate,
) {

    suspend operator fun invoke(deviceId: UUID): Result<Unit, SyncError> =
        stepUpGate.run { syncRepository.deleteDevice(deviceId) }
}
