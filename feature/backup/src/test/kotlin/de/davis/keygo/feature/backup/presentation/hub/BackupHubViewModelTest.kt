package de.davis.keygo.feature.backup.presentation.hub

import de.davis.keygo.core.security.crypto.FakeKeyStoreManager
import de.davis.keygo.feature.backup.FakeBackupArkKeyStore
import de.davis.keygo.feature.backup.FakeBackupJobRepository
import de.davis.keygo.feature.backup.FakeDispatchedBackupRepository
import de.davis.keygo.feature.backup.FakePersistableUriManager
import de.davis.keygo.feature.backup.data.FakeBackupDestinationResolver
import de.davis.keygo.feature.backup.domain.BackupProvisioningLock
import de.davis.keygo.feature.backup.domain.model.BackupDestinationUri
import de.davis.keygo.feature.backup.domain.model.BackupJob
import de.davis.keygo.feature.backup.domain.model.BackupResult
import de.davis.keygo.feature.backup.domain.model.BackupWorkStatus
import de.davis.keygo.feature.backup.domain.model.DispatchedBackup
import de.davis.keygo.feature.backup.domain.model.FileFormat
import de.davis.keygo.feature.backup.domain.usecase.CancelBackupUseCase
import de.davis.keygo.feature.backup.domain.usecase.CleanupBackupResourcesUseCase
import de.davis.keygo.feature.backup.domain.usecase.ObserveDispatchedBackupsUseCase
import de.davis.keygo.feature.backup.domain.usecase.ObserveLastBackupUseCaseImpl
import de.davis.keygo.feature.backup.presentation.hub.model.BackupHubEvent
import de.davis.keygo.feature.backup.presentation.hub.model.BackupHubUiEvent
import de.davis.keygo.feature.backup.presentation.hub.model.BackupSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class BackupHubViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeDispatchedBackupRepository()
    private val jobRepository = FakeBackupJobRepository()
    private val destinationResolver = FakeBackupDestinationResolver()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = BackupHubViewModel(
        observeDispatchedBackups =
            ObserveDispatchedBackupsUseCase(repository, jobRepository, destinationResolver),
        observeLastBackup = ObserveLastBackupUseCaseImpl(jobRepository, destinationResolver),
        cancelBackup = CancelBackupUseCase(
            repository = repository,
            jobRepository = jobRepository,
            cleanupBackupResources = CleanupBackupResourcesUseCase(
                jobRepository = jobRepository,
                arkKeyStore = FakeBackupArkKeyStore(),
                keyStoreManager = FakeKeyStoreManager(),
                persistableUriManager = FakePersistableUriManager(),
                provisioningLock = BackupProvisioningLock(),
            ),
        ),
    )

    @Test
    fun `dispatched workers are grouped in the ui state`() = runTest(dispatcher) {
        repository.statuses.value = listOf(
            BackupWorkStatus("w1", DispatchedBackup.Kind.OneTime, DispatchedBackup.State.Running()),
        )
        val vm = viewModel()

        val state = vm.state.first { it.hasItems }

        assertEquals(BackupSection.InProgress, state.groups.single().section)
        assertEquals("w1", state.groups.single().items.single().id)
    }

    @Test
    fun `last successful backup reaches the ui state`() = runTest(dispatcher) {
        jobRepository.jobs["done"] = BackupJob(
            uri = BackupDestinationUri("content://done.json"),
            wrappedPassphrase = null,
            format = FileFormat.JSON,
            finishedAt = 500L,
            lastResult = BackupResult.Success,
        )
        val vm = viewModel()

        val state = vm.state.first { it.lastBackup != null }

        assertEquals(500L, state.lastBackup?.finishedAt)
    }

    @Test
    fun `cancel event forwards id to the use case`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onEvent(BackupHubUiEvent.OnCancelBackup("w1", DispatchedBackup.Kind.OneTime))
        advanceUntilIdle()

        assertEquals(listOf("w1"), repository.cancelledIds)
    }

    @Test
    fun `OnRestoreBackup emits NavigateToImport`() = runTest {
        val viewModel = viewModel()

        viewModel.onEvent(BackupHubUiEvent.OnRestoreBackup)

        assertEquals(BackupHubEvent.NavigateToImport, viewModel.event.first())
    }
}
