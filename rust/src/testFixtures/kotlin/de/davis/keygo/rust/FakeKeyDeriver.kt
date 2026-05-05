package de.davis.keygo.rust

import de.davisalessandro.keygo.rust.KeyDerivationException
import de.davisalessandro.keygo.rust.KeyDeriverInterface
import de.davisalessandro.keygo.rust.RootKek
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * In-memory [KeyDeriverInterface] for tests.
 *
 * Derivation is deterministic (SHA-256 of password + salt), so a KEK derived for the same
 * (password, salt) pair round-trips with [FakeKeyWrapper]. Set [failDerivation] to force the
 * next call to throw [KeyDerivationException.Failed].
 */
class FakeKeyDeriver : KeyDeriverInterface {

    var failDerivation: Boolean = false

    override fun deriveRootKekFromPassword(password: String, salt: ByteArray): RootKek {
        if (failDerivation) throw KeyDerivationException.Failed("forced")
        return digest(password.toByteArray() + salt)
    }

    override fun deriveRootKekFromRecoveryKey(recoveryKey: ByteArray, salt: ByteArray): RootKek {
        if (failDerivation) throw KeyDerivationException.Failed("forced")
        return digest(recoveryKey + salt)
    }

    override fun generateSalt(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }

    private fun digest(input: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(input)
}
