package de.davis.keygo.core.sync.presentation.stepup

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import de.davis.keygo.core.sync.domain.model.SyncError
import de.davis.keygo.core.sync.domain.stepup.StepUpPrompt
import de.davis.keygo.core.sync.domain.stepup.StepUpPrompter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class StepUpViewModelTest {

    private val prompter = RecordingPrompter()
    private lateinit var viewModel: StepUpViewModel

    @OptIn(ExperimentalCoroutinesApi::class)
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = StepUpViewModel(prompter)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the prompt mirrors the prompter`() {
        assertNull(viewModel.prompt.value)

        val prompt = StepUpPrompt(error = SyncError.InvalidCredentials)
        prompter.prompt.value = prompt

        assertEquals(prompt, viewModel.prompt.value)

        prompter.prompt.value = null

        assertNull(viewModel.prompt.value)
    }

    @Test
    fun `submit forwards the typed password and clears the field`() {
        prompter.prompt.value = StepUpPrompt()
        viewModel.password.setTextAndPlaceCursorAtEnd("hunter2")

        viewModel.onSubmit()

        assertEquals(listOf("hunter2"), prompter.submitted)
        assertEquals(0, prompter.cancels)
        assertEquals("", viewModel.password.text.toString())
    }

    @Test
    fun `submit with an empty field does nothing`() {
        prompter.prompt.value = StepUpPrompt()

        viewModel.onSubmit()

        assertEquals(emptyList(), prompter.submitted)
    }

    @Test
    fun `cancel forwards to the prompter and clears the field`() {
        prompter.prompt.value = StepUpPrompt()
        viewModel.password.setTextAndPlaceCursorAtEnd("hunter2")

        viewModel.onCancel()

        assertEquals(1, prompter.cancels)
        assertEquals(emptyList(), prompter.submitted)
        assertEquals("", viewModel.password.text.toString())
    }

    @Test
    fun `an abandoned prompt clears what was typed`() {
        prompter.prompt.value = StepUpPrompt()
        viewModel.password.setTextAndPlaceCursorAtEnd("hunter2")

        prompter.prompt.value = null

        assertEquals("", viewModel.password.text.toString())
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Test
    fun `a closed prompt leaves nothing to undo`() {
        prompter.prompt.value = StepUpPrompt()
        viewModel.password.setTextAndPlaceCursorAtEnd("hunter2")
        viewModel.onSubmit()
        assertFalse(viewModel.password.undoState.canUndo)

        prompter.prompt.value = StepUpPrompt()
        viewModel.password.setTextAndPlaceCursorAtEnd("hunter3")
        viewModel.onCancel()
        assertFalse(viewModel.password.undoState.canUndo)

        prompter.prompt.value = StepUpPrompt()
        viewModel.password.setTextAndPlaceCursorAtEnd("hunter4")
        prompter.prompt.value = null
        assertFalse(viewModel.password.undoState.canUndo)
    }

    @Test
    fun `a prompt changing state keeps what was typed`() {
        prompter.prompt.value = StepUpPrompt()
        viewModel.password.setTextAndPlaceCursorAtEnd("hunter2")

        prompter.prompt.value = StepUpPrompt(error = SyncError.RateLimited)

        assertEquals("hunter2", viewModel.password.text.toString())
    }

    private class RecordingPrompter : StepUpPrompter {
        override val prompt = MutableStateFlow<StepUpPrompt?>(null)
        val submitted = mutableListOf<String>()
        var cancels = 0

        override fun submit(password: String) {
            submitted += password
        }

        override fun cancel() {
            cancels++
        }

        override fun abandon() = Unit
    }
}
