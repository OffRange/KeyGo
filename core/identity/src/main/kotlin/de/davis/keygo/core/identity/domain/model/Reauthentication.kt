package de.davis.keygo.core.identity.domain.model

/**
 * Proof of identity supplied when changing the master password.
 *
 * The password branch is resolved entirely in the domain ([ChangePasswordUseCase] derives the KEK
 * and unwraps the ARK). The biometric branch's ARK is recovered in the presentation layer via
 * `BiometricCryptoController.requestUnwrap` (an Android Keystore operation) and handed in here as
 * raw bytes; both the caller and the use case zero the array after use.
 */
sealed interface Reauthentication {

    data class Password(val currentPassword: String) : Reauthentication

    class Biometric(val recoveredArk: ByteArray) : Reauthentication
}
