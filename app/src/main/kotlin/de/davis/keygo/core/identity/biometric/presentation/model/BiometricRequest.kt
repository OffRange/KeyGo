package de.davis.keygo.core.identity.biometric.presentation.model

import de.davis.keygo.core.presentation.UIText
import javax.crypto.Cipher

@Deprecated("Migrate to :core:security")
sealed interface BiometricRequest {

    val title: UIText
    val negativeButtonText: UIText

    data class Class3(
        override val title: UIText,
        override val negativeButtonText: UIText,
        val cipher: Cipher,
    ) : BiometricRequest
}
