package de.davis.keygo.rust

import de.davisalessandro.keygo.rust.Account
import de.davisalessandro.keygo.rust.AccountManagerInterface
import de.davisalessandro.keygo.rust.CreateAccount
import de.davisalessandro.keygo.rust.Vault
import java.security.SecureRandom
import java.util.UUID

/**
 * In-memory [AccountManagerInterface] for tests. Each [createAccount] call yields fresh random
 * ARK/vault-key material and new UUIDs.
 */
class FakeAccountManager : AccountManagerInterface {

    override fun createAccount(): CreateAccount = CreateAccount(
        account = Account(id = UUID.randomUUID(), ark = randomKey()),
        defaultVault = Vault(id = UUID.randomUUID(), vaultKey = randomKey()),
    )

    private fun randomKey(): ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }
}