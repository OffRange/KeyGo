package de.davis.keygo.feature.vault.domain.usecase

import de.davis.keygo.core.item.FakeVaultRepository
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Vault
import de.davis.keygo.core.util.isFailure
import de.davis.keygo.core.util.isSuccess
import de.davis.keygo.feature.vault.domain.model.VaultCreationError
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EditVaultUseCaseTest {

    private val vaultRepository = FakeVaultRepository()
    private val useCase = EditVaultUseCase(vaultRepository)

    private val existingVault = Vault(
        id = newVaultId(),
        name = "Original",
        keyInformation = KeyInformation(byteArrayOf(), byteArrayOf()),
        icon = Vault.Icon.Default,
    )

    @Test
    fun `returns BlankName when name is empty`() = runTest {
        val result = useCase(existingVault.id, name = "", icon = Vault.Icon.Default)

        assertTrue(result.isFailure())
        assertEquals(VaultCreationError.BlankName, result.error)
    }

    @Test
    fun `returns BlankName when name is whitespace only`() = runTest {
        val result = useCase(existingVault.id, name = "   ", icon = Vault.Icon.Default)

        assertTrue(result.isFailure())
        assertEquals(VaultCreationError.BlankName, result.error)
    }

    @Test
    fun `does not update vault when name is blank`() = runTest {
        vaultRepository.seed(existingVault)

        useCase(existingVault.id, name = "", icon = Vault.Icon.Work)

        val stored = vaultRepository.observeVaults().first().single()
        assertEquals("Original", stored.name)
        assertEquals(Vault.Icon.Default, stored.icon)
    }

    @Test
    fun `returns Success for valid name`() = runTest {
        vaultRepository.seed(existingVault)

        val result = useCase(existingVault.id, name = "Updated", icon = Vault.Icon.Default)

        assertTrue(result.isSuccess())
    }

    @Test
    fun `updates vault name`() = runTest {
        vaultRepository.seed(existingVault)

        useCase(existingVault.id, name = "Updated", icon = Vault.Icon.Default)

        val stored = vaultRepository.observeVaults().first().single()
        assertEquals("Updated", stored.name)
    }

    @Test
    fun `trims leading and trailing whitespace from name before storing`() = runTest {
        vaultRepository.seed(existingVault)

        useCase(existingVault.id, name = "  Trimmed  ", icon = Vault.Icon.Default)

        val stored = vaultRepository.observeVaults().first().single()
        assertEquals("Trimmed", stored.name)
    }

    @Test
    fun `name with only internal whitespace is stored as-is`() = runTest {
        vaultRepository.seed(existingVault)

        useCase(existingVault.id, name = "My Vault", icon = Vault.Icon.Default)

        val stored = vaultRepository.observeVaults().first().single()
        assertEquals("My Vault", stored.name)
    }

    @Test
    fun `updates vault icon`() = runTest {
        vaultRepository.seed(existingVault)

        useCase(existingVault.id, name = "Original", icon = Vault.Icon.Work)

        val stored = vaultRepository.observeVaults().first().single()
        assertEquals(Vault.Icon.Work, stored.icon)
    }

    @Test
    fun `does not change vault id`() = runTest {
        vaultRepository.seed(existingVault)

        useCase(existingVault.id, name = "Renamed", icon = Vault.Icon.Default)

        val stored = vaultRepository.observeVaults().first().single()
        assertEquals(existingVault.id, stored.id)
    }

    @Test
    fun `does not change key information`() = runTest {
        val keyInfo = KeyInformation(
            wrappedKey = byteArrayOf(1, 2, 3),
            keyNonce = byteArrayOf(4, 5, 6),
        )
        val vault = Vault(
            id = newVaultId(),
            name = "Secure",
            keyInformation = keyInfo,
            icon = Vault.Icon.Default,
        )
        vaultRepository.seed(vault)

        useCase(vault.id, name = "Renamed", icon = Vault.Icon.Home)

        val stored = vaultRepository.observeVaults().first().single()
        assertTrue(stored.keyInformation.wrappedKey.contentEquals(keyInfo.wrappedKey))
        assertTrue(stored.keyInformation.keyNonce.contentEquals(keyInfo.keyNonce))
    }

    @Test
    fun `can update to every available icon`() = runTest {
        vaultRepository.seed(existingVault)

        Vault.Icon.entries.forEach { icon ->
            useCase(existingVault.id, name = "V", icon = icon)
            assertEquals(icon, vaultRepository.observeVaults().first().single().icon)
        }
    }

    @Test
    fun `updating a single-character name succeeds`() = runTest {
        vaultRepository.seed(existingVault)

        val result = useCase(existingVault.id, name = "X", icon = Vault.Icon.Default)

        assertTrue(result.isSuccess())
        assertEquals("X", vaultRepository.observeVaults().first().single().name)
    }
}
