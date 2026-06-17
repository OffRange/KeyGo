package de.davis.keygo.feature.backup.presentation.export

import de.davis.keygo.core.item.FakePasswordStrengthEstimator
import de.davis.keygo.feature.backup.data.FakeBackupDestinationResolver
import de.davis.keygo.feature.backup.domain.model.BackupDestination
import de.davis.keygo.feature.backup.domain.model.BackupDestinationUri
import de.davis.keygo.feature.backup.domain.model.FileFormat
import de.davis.keygo.feature.backup.presentation.export.model.ExportWizardEvent
import de.davis.keygo.feature.backup.presentation.export.model.ExportWizardUiEvent
import de.davis.keygo.feature.backup.presentation.export.model.ScheduleMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ExportWizardViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val resolver = FakeBackupDestinationResolver()
    private val estimator = FakePasswordStrengthEstimator()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = ExportWizardViewModel(
        backupDestinationResolver = resolver,
        passwordStrengthEstimator = estimator,
    )

    @Test
    fun `choosing a destination with the default schedule mode opens the folder picker`() =
        runTest(dispatcher) {
            val vm = viewModel()

            vm.onEvent(ExportWizardUiEvent.ChooseDestination)

            assertEquals(ExportWizardEvent.PickFolder, vm.event.first())
        }

    @Test
    fun `choosing a destination for a recurring backup opens the folder picker`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.onEvent(ExportWizardUiEvent.ScheduleModeSelected(ScheduleMode.Recurring))

            vm.onEvent(ExportWizardUiEvent.ChooseDestination)

            assertEquals(ExportWizardEvent.PickFolder, vm.event.first())
        }

    @Test
    fun `choosing a destination for a one-time kdbx backup creates a kdbx file`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.onEvent(ExportWizardUiEvent.FileFormatSelected(FileFormat.KDBX))
            vm.onEvent(ExportWizardUiEvent.ScheduleModeSelected(ScheduleMode.OneTime))

            vm.onEvent(ExportWizardUiEvent.ChooseDestination)

            assertEquals(
                ExportWizardEvent.CreateFile(suggestedName = "keygo-backup.kdbx"),
                vm.event.first(),
            )
        }

    @Test
    fun `choosing a destination for a one-time csv backup creates a csv file`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.onEvent(ExportWizardUiEvent.FileFormatSelected(FileFormat.CSV))
            vm.onEvent(ExportWizardUiEvent.ScheduleModeSelected(ScheduleMode.OneTime))

            vm.onEvent(ExportWizardUiEvent.ChooseDestination)

            assertEquals(
                ExportWizardEvent.CreateFile(suggestedName = "keygo-backup.csv"),
                vm.event.first(),
            )
        }

    @Test
    fun `picking a destination resolves it and stores it in state`() =
        runTest(dispatcher) {
            val resolved = BackupDestination(
                provider = BackupDestination.Provider.OnDevice,
                displayPath = "Internal storage/Download",
                fileName = "keygo-backup.kdbx",
            )
            resolver.result = resolved
            val vm = viewModel()

            vm.onDestinationPicked(BackupDestinationUri("content://example/document/1"))

            val state = vm.state.first { it.destinationState.destination != null }
            assertEquals(resolved, state.destinationState.destination)
        }

    @Test
    fun `picking a null destination leaves state unchanged`() =
        runTest(dispatcher) {
            val vm = viewModel()

            vm.onDestinationPicked(null)

            assertEquals(null, resolver.lastUri)
            assertEquals(null, vm.state.first().destinationState.destination)
        }

    @Test
    fun `selecting a non-encrypted format forces one-time and disables recurring`() =
        runTest(dispatcher) {
            val vm = viewModel()

            vm.onEvent(ExportWizardUiEvent.FileFormatSelected(FileFormat.CSV))

            val schedule = vm.state
                .first { it.scheduleState.mode == ScheduleMode.OneTime }
                .scheduleState
            assertEquals(ScheduleMode.OneTime, schedule.mode)
            assertEquals(false, schedule.recurringAllowed)
        }

    @Test
    fun `re-selecting an encrypted format re-enables recurring without overriding one-time`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.onEvent(ExportWizardUiEvent.FileFormatSelected(FileFormat.CSV))

            vm.onEvent(ExportWizardUiEvent.FileFormatSelected(FileFormat.KDBX))

            val schedule = vm.state
                .first { it.scheduleState.mode == ScheduleMode.OneTime }
                .scheduleState
            assertEquals(true, schedule.recurringAllowed)
            assertEquals(ScheduleMode.OneTime, schedule.mode)
        }

    @Test
    fun `selecting recurring mode is ignored when recurring is not allowed`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.onEvent(ExportWizardUiEvent.FileFormatSelected(FileFormat.CSV))

            vm.onEvent(ExportWizardUiEvent.ScheduleModeSelected(ScheduleMode.Recurring))

            val schedule = vm.state
                .first { it.scheduleState.mode == ScheduleMode.OneTime }
                .scheduleState
            assertEquals(ScheduleMode.OneTime, schedule.mode)
        }
}
