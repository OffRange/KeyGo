package de.davis.keygo.core.item.data.maper

import de.davis.keygo.core.item.data.local.entity.VaultEntity
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Vault
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import de.davis.keygo.core.item.data.local.entity.KeyInformation as EntityKeyInformation

class VaultMapperTest {

    private fun testVault(name: String = "Personal") = Vault(
        id = newVaultId(),
        name = name,
        keyInformation = KeyInformation(
            wrappedKey = byteArrayOf(1, 2, 3),
            keyNonce = byteArrayOf(4, 5, 6),
        ),
        icon = Vault.Icon.Default,
    )

    // toData

    @Test
    fun `toData preserves id`() {
        val vault = testVault()
        assertEquals(vault.id, vault.toData().id)
    }

    @Test
    fun `toData preserves name`() {
        val vault = testVault(name = "Work")
        assertEquals("Work", vault.toData().name)
    }

    @Test
    fun `toData copies keyInformation bytes`() {
        val vault = testVault()
        val entity = vault.toData()

        assertContentEquals(vault.keyInformation.wrappedKey, entity.keyInformation.wrappedKey)
        assertContentEquals(vault.keyInformation.keyNonce, entity.keyInformation.keyNonce)
    }

    // toDomain

    @Test
    fun `toDomain preserves id`() {
        val id = newVaultId()
        val entity = VaultEntity(
            id = id,
            name = "Personal",
            keyInformation = EntityKeyInformation(byteArrayOf(), byteArrayOf()),
            icon = Vault.Icon.Default,
        )

        assertEquals(id, entity.toDomain().id)
    }

    @Test
    fun `toDomain preserves name`() {
        val entity = VaultEntity(
            id = newVaultId(),
            name = "Family",
            keyInformation = EntityKeyInformation(byteArrayOf(), byteArrayOf()),
            icon = Vault.Icon.Default,
        )

        assertEquals("Family", entity.toDomain().name)
    }

    @Test
    fun `toDomain copies keyInformation bytes`() {
        val entity = VaultEntity(
            id = newVaultId(),
            name = "Test",
            keyInformation = EntityKeyInformation(
                wrappedKey = byteArrayOf(10, 20),
                keyNonce = byteArrayOf(30, 40),
            ),
            icon = Vault.Icon.Default,
        )

        val domain = entity.toDomain()
        assertContentEquals(byteArrayOf(10, 20), domain.keyInformation.wrappedKey)
        assertContentEquals(byteArrayOf(30, 40), domain.keyInformation.keyNonce)
        assertEquals(Vault.Icon.Default, domain.icon)
    }

    // round-trip

    @Test
    fun `round-trip domain-entity-domain preserves all fields`() {
        val original = testVault()
        val roundTripped = original.toData().toDomain()

        assertEquals(original.id, roundTripped.id)
        assertEquals(original.name, roundTripped.name)
        assertContentEquals(
            original.keyInformation.wrappedKey,
            roundTripped.keyInformation.wrappedKey
        )
        assertContentEquals(original.keyInformation.keyNonce, roundTripped.keyInformation.keyNonce)
    }
}
