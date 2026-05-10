package de.davis.keygo.core.item.data.mapper

import de.davis.keygo.core.item.data.local.entity.credential.PasskeyEntity
import de.davis.keygo.core.item.data.local.pojo.PasskeyMetadataPojo
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.domain.model.EncryptedPayload
import de.davis.keygo.core.item.domain.model.Passkey
import de.davis.keygo.core.item.domain.model.PasskeyUser
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PasskeyMapperTest {

    private val credentialId = byteArrayOf(1, 2, 3, 4)
    private val privateKey = Passkey.PrivateKey(
        EncryptedPayload(
            ciphertext = byteArrayOf(0x1, 0x2, 0x3),
            iv = byteArrayOf(0x11, 0x12, 0x13)
        )
    )
    private val user = PasskeyUser(name = "alice", displayName = "Alice Smith")

    private fun testPasskey() = Passkey(
        credentialId = credentialId,
        rp = "example.com",
        privateKey = privateKey,
        loginId = newItemId(),
        user = user,
    )

    // Passkey.toData()

    @Test
    fun `toData copies credentialId`() {
        val entity = testPasskey().toData()
        assertContentEquals(credentialId, entity.credentialId)
    }

    @Test
    fun `toData copies rp`() {
        assertEquals("example.com", testPasskey().toData().rp)
    }

    @Test
    fun `toData copies privateKey`() {
        assertEquals(privateKey.payload, testPasskey().toData().privateKey)
    }

    @Test
    fun `toData copies loginId`() {
        val passkey = testPasskey()
        assertEquals(passkey.loginId, passkey.toData().loginId)
    }

    @Test
    fun `toData flattens user name and displayName`() {
        val entity = testPasskey().toData()
        assertEquals("alice", entity.name)
        assertEquals("Alice Smith", entity.displayName)
    }

    // PasskeyEntity.toDomain()

    @Test
    fun `PasskeyEntity toDomain copies credentialId`() {
        val entity = passkeyEntity()
        assertContentEquals(credentialId, entity.toDomain().credentialId)
    }

    @Test
    fun `PasskeyEntity toDomain copies rp`() {
        assertEquals("example.com", passkeyEntity().toDomain().rp)
    }

    @Test
    fun `PasskeyEntity toDomain reconstructs user`() {
        val domain = passkeyEntity(name = "bob", displayName = "Bob Jones").toDomain()
        assertEquals("bob", domain.user.name)
        assertEquals("Bob Jones", domain.user.displayName)
    }

    @Test
    fun `round-trip Passkey-entity-domain preserves equality`() {
        val original = testPasskey()
        assertEquals(original, original.toData().toDomain())
    }

    // PasskeyMetadataPojo.toDomain()

    @Test
    fun `PasskeyMetadataPojo toDomain copies vaultName`() {
        val metadata = pojo(vaultName = "Personal").toDomain()
        assertEquals("Personal", metadata.vaultName)
    }

    @Test
    fun `PasskeyMetadataPojo toDomain copies credentialId`() {
        val metadata = pojo().toDomain()
        assertContentEquals(credentialId, metadata.credentialId)
    }

    @Test
    fun `PasskeyMetadataPojo toDomain reconstructs user`() {
        val metadata = pojo(name = "carol", displayName = "Carol White").toDomain()
        assertEquals("carol", metadata.user.name)
        assertEquals("Carol White", metadata.user.displayName)
    }

    @Test
    fun `PasskeyMetadataPojo toDomain preserves null pwdUsername`() {
        assertNull(pojo(pwdUsername = null).toDomain().passwordUsername)
    }

    @Test
    fun `PasskeyMetadataPojo toDomain preserves non-null pwdUsername`() {
        assertEquals(
            "alice@example.com",
            pojo(pwdUsername = "alice@example.com").toDomain().passwordUsername
        )
    }

    // Helpers

    private fun passkeyEntity(
        name: String = "alice",
        displayName: String = "Alice Smith",
    ) = PasskeyEntity(
        credentialId = credentialId,
        rp = "example.com",
        privateKey = privateKey.payload,
        loginId = newItemId(),
        name = name,
        displayName = displayName,
    )

    private fun pojo(
        vaultName: String = "Personal",
        pwdUsername: String? = "alice@example.com",
        name: String = "alice",
        displayName: String = "Alice Smith",
    ) = PasskeyMetadataPojo(
        vaultName = vaultName,
        pwdUsername = pwdUsername,
        name = name,
        displayName = displayName,
        credentialId = credentialId,
    )
}
