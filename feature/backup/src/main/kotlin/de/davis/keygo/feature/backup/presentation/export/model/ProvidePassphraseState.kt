package de.davis.keygo.feature.backup.presentation.export.model

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Stable
import de.davis.keygo.core.item.domain.model.PasswordScore

@Stable
internal data class ProvidePassphraseState(
    val passphraseTextFieldState: TextFieldState,
    val confirmPassphraseTextFieldState: TextFieldState,
    val passphraseScore: PasswordScore = PasswordScore.None,
    val valid: Boolean = false,
)