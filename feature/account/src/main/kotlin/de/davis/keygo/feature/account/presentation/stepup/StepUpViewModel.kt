package de.davis.keygo.feature.account.presentation.stepup

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.lifecycle.ViewModel
import de.davis.keygo.core.sync.domain.stepup.StepUpPrompt
import de.davis.keygo.core.sync.domain.stepup.StepUpPrompter
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class StepUpViewModel(
    private val prompter: StepUpPrompter,
) : ViewModel() {

    val prompt: StateFlow<StepUpPrompt?> = prompter.prompt
    val password = TextFieldState()

    fun onSubmit() {
        val typed = password.text.toString()
        if (typed.isEmpty()) return
        password.clearText()
        prompter.submit(typed)
    }

    fun onCancel() {
        password.clearText()
        prompter.cancel()
    }
}
