package de.davis.keygo.core.identity.biometric.presentation.model

import de.davis.keygo.core.domain.model.VaultItem
import de.davis.keygo.core.presentation.UIText
import javax.crypto.Cipher

sealed interface BiometricRequest {

    val title: UIText
    val negativeButtonText: UIText
    val reason: Reason

    data class Class3(
        override val title: UIText,
        override val negativeButtonText: UIText,
        override val reason: Reason,
        val cipher: Cipher,
    ) : BiometricRequest


    sealed interface Reason {
        data object UnlockSession : Reason
        data class UnlockItem(val vaultItem: VaultItem) : Reason
    }
}
