package de.davis.keygo.core.sync.presentation.stepup

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.davis.keygo.core.sync.domain.stepup.StepUpPrompt
import de.davis.keygo.core.sync.domain.stepup.StepUpPrompter
import de.davis.keygo.core.ui.text.clearSecret
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
            prompt.collect { if (it == null) password.clearSecret() }
        }
    }

    fun onSubmit() {
        val typed = password.text.toString()
        if (typed.isEmpty()) return
        password.clearSecret()
        prompter.submit(typed)
    }

    fun onCancel() {
        password.clearSecret()
        prompter.cancel()
    }
}
