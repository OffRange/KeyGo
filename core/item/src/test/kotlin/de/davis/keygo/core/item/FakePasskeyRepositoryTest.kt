package de.davis.keygo.core.item

import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.domain.model.EncryptedPayload
import de.davis.keygo.core.item.domain.model.Passkey
import de.davis.keygo.core.item.domain.model.PasskeyUser
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FakePasskeyRepositoryTest {

    private val repository = FakePasskeyRepository()

    private fun passkey(loginId: de.davis.keygo.core.item.domain.alias.ItemId, rp: String) = Passkey(
        credentialId = rp.encodeToByteArray(),
        rp = rp,
        privateKey = Passkey.PrivateKey(EncryptedPayload(byteArrayOf(1), byteArrayOf(2))),
        loginId = loginId,
        user = PasskeyUser(name = "alice", displayName = "Alice"),
    )

    @Test
    fun `returns every passkey of the requested login`() = runTest {
        val login = newItemId()
        val other = newItemId()
        repository.seed(
            passkey(login, "example.com"),
            passkey(login, "example.org"),
            passkey(other, "elsewhere.test"),
        )

        val passkeys = repository.getPasskeysByLogin(login)

        assertEquals(listOf("example.com", "example.org"), passkeys.map { it.rp })
    }

    @Test
    fun `returns nothing for a login without passkeys`() = runTest {
        assertEquals(emptyList(), repository.getPasskeysByLogin(newItemId()))
    }
}
