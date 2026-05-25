package de.davis.keygo.feature.credit_card.presentation

import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.credit_card.FakeApduTransport
import de.davis.keygo.feature.credit_card.FakeCardReaderRepository
import de.davis.keygo.feature.credit_card.domain.model.Card
import de.davis.keygo.feature.credit_card.domain.model.CardReadFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.time.YearMonth
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CardScanViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val card = Card(
        holder = "JANE DOE",
        cardNumber = "4111111111111111",
        expiry = YearMonth.of(2030, 12),
    )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun startsReady() = runTest(dispatcher) {
        val vm = CardScanViewModel(FakeCardReaderRepository(Result.Success(card)))
        assertEquals(CardScanUiState.Ready, vm.state.value)
    }

    @Test
    fun successfulReadGoesReadingThenSuccess() = runTest(dispatcher) {
        val vm = CardScanViewModel(FakeCardReaderRepository(Result.Success(card)))
        vm.onTransport(FakeApduTransport())
        assertIs<CardScanUiState.Reading>(vm.state.value)
        advanceUntilIdle()
        assertEquals(CardScanUiState.Success(card), vm.state.value)
    }

    @Test
    fun failedReadEmitsFailure() = runTest(dispatcher) {
        val vm = CardScanViewModel(
            FakeCardReaderRepository(Result.Failure(CardReadFailure.NoReadableData)),
        )
        vm.onTransport(FakeApduTransport())
        advanceUntilIdle()
        assertEquals(CardScanUiState.Failure(CardReadFailure.NoReadableData), vm.state.value)
    }

    @Test
    fun nullTransportEmitsNotAnEmvCard() = runTest(dispatcher) {
        val vm = CardScanViewModel(FakeCardReaderRepository(Result.Success(card)))
        vm.onTransport(null)
        assertEquals(CardScanUiState.Failure(CardReadFailure.NotAnEmvCard), vm.state.value)
    }

    @Test
    fun ignoresNewTransportWhileReading() = runTest(dispatcher) {
        val repository = FakeCardReaderRepository(Result.Success(card))
        val vm = CardScanViewModel(repository)
        vm.onTransport(FakeApduTransport())
        vm.onTransport(FakeApduTransport())
        advanceUntilIdle()
        assertEquals(1, repository.readCount)
    }

    @Test
    fun resetReturnsToReady() = runTest(dispatcher) {
        val vm = CardScanViewModel(
            FakeCardReaderRepository(Result.Failure(CardReadFailure.TagLost)),
        )
        vm.onTransport(FakeApduTransport())
        advanceUntilIdle()
        vm.reset()
        assertEquals(CardScanUiState.Ready, vm.state.value)
    }
}
