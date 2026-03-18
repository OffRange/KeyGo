package de.davis.keygo.autofill.presentation.activity.model

import de.davis.keygo.core.security.domain.model.BiometricString

internal sealed interface AutofillBiometricRequest {
    val title: BiometricString.Title
    val negativeButton: BiometricString.NegativeButton

    data class UnlockItem(val itemName: String) : AutofillBiometricRequest {
        override val title = BiometricString.Title.UnlockItem(itemName)
        override val negativeButton = BiometricString.NegativeButton.Password
    }
}