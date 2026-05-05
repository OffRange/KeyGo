package de.davis.keygo.core.item.data.maper

import de.davis.keygo.core.item.data.local.entity.DomainInfoEntity
import de.davis.keygo.core.item.data.local.entity.ItemEntity
import de.davis.keygo.core.item.data.local.entity.PasswordEntity
import de.davis.keygo.core.item.data.local.pojo.LightweightPassword
import de.davis.keygo.core.item.data.local.pojo.RP
import de.davis.keygo.core.item.data.local.pojo.VaultPassword
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Password
import de.davis.keygo.core.item.domain.model.SecretData
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import de.davis.keygo.core.item.data.local.entity.KeyInformation as EntityKeyInformation

class PasswordMapperTest {

    private fun testPassword(
        username: String? = "user@example.com",
        score: Password.Score = Password.Score.Strong,
        totpSecret: SecretData<String>? = null,
    ) = Password(
        id = newItemId(),
        name = "My password",
        username = username,
        domainInfos = emptySet(),
        score = score,
        password = SecretData.EMPTY_STRING,
        totpSecret = totpSecret,
        note = "A note",
        pinned = false,
        vaultId = newVaultId(),
        keyInformation = KeyInformation(byteArrayOf(), byteArrayOf()),
    )

    private fun entityKeyInfo() = EntityKeyInformation(byteArrayOf(), byteArrayOf())

    // Password.toData() → PasswordEntity

    @Test
    fun `toData preserves id`() {
        val password = testPassword()
        assertEquals(password.id, password.toData().id)
    }

    @Test
    fun `toData preserves username`() {
        val password = testPassword(username = "alice@example.com")
        assertEquals("alice@example.com", password.toData().username)
    }

    @Test
    fun `toData preserves null username`() {
        val password = testPassword(username = null)
        assertNull(password.toData().username)
    }

    @Test
    fun `toData preserves score`() {
        val password = testPassword(score = Password.Score.Excellent)
        assertEquals(Password.Score.Excellent, password.toData().score)
    }

    @Test
    fun `toData preserves null totpSecret`() {
        assertNull(testPassword(totpSecret = null).toData().totpSecret)
    }

    @Test
    fun `toData preserves non-null totpSecret`() {
        val secret = SecretData.EMPTY_STRING
        assertEquals(secret, testPassword(totpSecret = secret).toData().totpSecret)
    }

    // VaultPassword.toDomain() → Password

    @Test
    fun `VaultPassword toDomain copies id from passwordEntity`() {
        val id = newItemId()
        val vaultPassword = vaultPassword(id = id)

        assertEquals(id, vaultPassword.toDomain().id)
    }

    @Test
    fun `VaultPassword toDomain copies username`() {
        val vaultPassword = vaultPassword(username = "bob@example.com")
        assertEquals("bob@example.com", vaultPassword.toDomain().username)
    }

    @Test
    fun `VaultPassword toDomain copies name and note from itemEntity`() {
        val vaultPassword = vaultPassword(name = "Work site", note = "Remember this")
        val domain = vaultPassword.toDomain()

        assertEquals("Work site", domain.name)
        assertEquals("Remember this", domain.note)
    }

    @Test
    fun `VaultPassword toDomain maps domains`() {
        val domainEntity = DomainInfoEntity(
            passwordId = newItemId(),
            value = "https://example.com",
            eTLD1 = "example.com",
        )
        val vaultPassword = vaultPassword(domains = listOf(domainEntity))

        assertEquals(1, vaultPassword.toDomain().domainInfos.size)
        assertEquals("https://example.com", vaultPassword.toDomain().domainInfos.first().value)
    }

    @Test
    fun `VaultPassword toDomain maps passkeyRPs`() {
        val vaultPassword = vaultPassword(rpEntities = listOf(RP("example.com"), RP("other.com")))
        val domain = vaultPassword.toDomain()

        assertEquals(setOf("example.com", "other.com"), domain.passkeyRPs)
    }

    @Test
    fun `VaultPassword toDomain with empty domains and rps`() {
        val domain = vaultPassword().toDomain()

        assertTrue(domain.domainInfos.isEmpty())
        assertTrue(domain.passkeyRPs.isEmpty())
    }

    // LightweightPassword.toDomain() → LitePassword

    @Test
    fun `LightweightPassword toDomain copies id, name, pinned, username`() {
        val id = newItemId()
        val lightweight = LightweightPassword(
            id = id,
            name = "Test",
            pinned = true,
            username = "carol@example.com",
            domains = emptyList(),
        )

        val lite = lightweight.toDomain()

        assertEquals(id, lite.id)
        assertEquals("Test", lite.name)
        assertEquals(true, lite.pinned)
        assertEquals("carol@example.com", lite.username)
    }

    @Test
    fun `LightweightPassword toDomain maps domains`() {
        val lightweight = LightweightPassword(
            id = newItemId(),
            name = "Test",
            pinned = false,
            username = null,
            domains = listOf(
                DomainInfoEntity(
                    passwordId = newItemId(),
                    value = "https://example.com",
                    eTLD1 = "example.com"
                ),
            ),
        )

        assertEquals(1, lightweight.toDomain().domains.size)
        assertEquals("https://example.com", lightweight.toDomain().domains.first().value)
    }

    // Helper

    private fun vaultPassword(
        id: ItemId = newItemId(),
        username: String? = null,
        name: String = "Test",
        note: String? = null,
        domains: List<DomainInfoEntity> = emptyList(),
        rpEntities: List<RP> = emptyList(),
    ): VaultPassword {
        val vaultId = newVaultId()
        return VaultPassword(
            passwordEntity = PasswordEntity(
                id = id,
                username = username,
                score = Password.Score.Strong,
                password = SecretData.EMPTY_STRING,
                totpSecret = null,
            ),
            itemEntity = ItemEntity(
                id = id,
                vaultId = vaultId,
                name = name,
                note = note,
                itemType = VaultItemType.Password,
                pinned = false,
                keyInformation = entityKeyInfo(),
            ),
            rpEntity = rpEntities,
            domains = domains,
        )
    }
}
