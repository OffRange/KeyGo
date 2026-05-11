package de.davis.keygo.rust

import de.davisalessandro.keygo.rust.Account
import de.davisalessandro.keygo.rust.AccountManagerInterface
import de.davisalessandro.keygo.rust.CreateAccount
import de.davisalessandro.keygo.rust.Vault
import java.util.UUID

/**
 * In-memory [AccountManagerInterface] for tests. [seedAccount] MUST be called before any
 * [createAccount] calls.
 */
class FakeAccountManager : AccountManagerInterface {

    var key: ByteArray = ByteArray(32) { it.toByte() }

    var createAccount: CreateAccount = CreateAccount(
        account = Account(
            id = UUID.randomUUID(),
            ark = ByteArray(32) { (it + 1).toByte() },
        ),
        defaultVault = Vault(
            id = UUID.randomUUID(),
            vaultKey = ByteArray(32) { (it + 2).toByte() },
        )
    )

    fun seedAccount(createAccount: CreateAccount) {
        this.createAccount = createAccount
    }

    fun seedKey(key: ByteArray) {
        this.key = key
    }

    override fun createAccount(): CreateAccount = createAccount

    private fun randomKey(): ByteArray = key
}