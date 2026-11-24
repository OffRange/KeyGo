package de.davis.keygo.feature.account.presentation.stepup

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.sync.domain.stepup.StepUpPrompt
import de.davis.keygo.core.sync.domain.stepup.StepUpPrompter
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class StepUpViewModel(
    private val prompter: StepUpPrompter,
) : ViewModel() {

    val prompt: StateFlow<StepUpPrompt?> = prompter.prompt
    val password = TextFieldState()

    init {
        // A prompt can end without the dialog (abandoned when the session ends); what was typed
        // must not outlive it, or wait in memory while locked and fill the next prompt.
        viewModelScope.launch {
            prompt.collect { if (it == null) password.clearText() }
        }
    }

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
