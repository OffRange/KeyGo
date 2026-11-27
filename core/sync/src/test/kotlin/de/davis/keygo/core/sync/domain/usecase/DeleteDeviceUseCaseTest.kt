package de.davis.keygo.core.sync.domain.usecase

import de.davis.keygo.core.sync.FakeStepUpGate
import de.davis.keygo.core.sync.FakeSyncRepository
import de.davis.keygo.core.sync.domain.model.SyncError
import de.davis.keygo.core.util.Result
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class DeleteDeviceUseCaseTest {

    private val syncRepository = FakeSyncRepository()
    private val stepUpGate = FakeStepUpGate()
    private val deleteDevice = DeleteDeviceUseCase(syncRepository, stepUpGate)

    @Test
    fun `removal goes through the gate and deletes the device`() = runTest {
        val id = UUID.randomUUID()

        val result = deleteDevice(id)

        assertEquals(Result.Success(Unit), result)
        assertEquals(listOf(id), syncRepository.deletedDevices)
        assertEquals(1, stepUpGate.runs)
        assertEquals(0, stepUpGate.forcedRuns)
    }

    @Test
    fun `a dismissed step-up deletes nothing`() = runTest {
        stepUpGate.cancels = true

        val result = deleteDevice(UUID.randomUUID())

        assertEquals(Result.Failure(SyncError.StepUpCancelled), result)
        assertEquals(emptyList(), syncRepository.deleteDeviceCalls)
    }

    @Test
    fun `a failed removal is returned as is`() = runTest {
        syncRepository.deleteDeviceResults = listOf(Result.Failure(SyncError.RateLimited))

        val result = deleteDevice(UUID.randomUUID())

        assertEquals(Result.Failure(SyncError.RateLimited), result)
        assertEquals(emptyList(), syncRepository.deletedDevices)
    }
}
