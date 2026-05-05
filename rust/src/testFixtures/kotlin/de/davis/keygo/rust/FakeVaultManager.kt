package de.davis.keygo.rust

import de.davisalessandro.keygo.rust.VaultKey
import de.davisalessandro.keygo.rust.VaultManagerInterface
import java.security.SecureRandom

/**
 * In-memory [VaultManagerInterface] for tests. Generates a random 32-byte key on each call so
 * successive calls produce distinct keys, mirroring the real implementation.
 */
class FakeVaultManager : VaultManagerInterface {
    override fun createNewVaultKey(): VaultKey =
        ByteArray(32).also { SecureRandom().nextBytes(it) }
}
