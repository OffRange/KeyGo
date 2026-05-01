package de.davis.keygo.feature.vault.domain.usecase

import de.davis.keygo.core.item.FakeVaultContextRepository
import de.davis.keygo.core.item.FakeVaultRepository
import de.davis.keygo.core.item.domain.model.Vault
import de.davis.keygo.core.item.domain.model.VaultContext
import de.davis.keygo.core.security.crypto.FakeSession
import de.davis.keygo.core.util.getOrNull
import de.davis.keygo.core.util.isFailure
import de.davis.keygo.core.util.isSuccess
import de.davis.keygo.feature.vault.domain.model.VaultCreationError
import de.davis.keygo.rust.FakeKeyWrapper
import de.davis.keygo.rust.FakeVaultManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CreateVaultUseCaseTest {

    private val session = FakeSession()

    private val vaultRepository = FakeVaultRepository()
    private val vaultContextRepository = FakeVaultContextRepository()
    private val vaultManager = FakeVaultManager()
    private val keyWrapper = FakeKeyWrapper()

    private val useCase = CreateVaultUseCase(
        vaultRepository = vaultRepository,
        vaultContextRepository = vaultContextRepository,
        vaultManager = vaultManager,
        keyWrapper = keyWrapper,
        session = session,
    )

    @Test
    fun `returns BlankName when name is empty`() = runTest {
        val result = useCase(name = "", icon = Vault.Icon.Default)

        assertTrue(result.isFailure())
        assertEquals(VaultCreationError.BlankName, result.error)
    }

    @Test
    fun `returns BlankName when name is whitespace only`() = runTest {
        val result = useCase(name = "   ", icon = Vault.Icon.Default)

        assertTrue(result.isFailure())
        assertEquals(VaultCreationError.BlankName, result.error)
    }

    @Test
    fun `does not persist a vault when name is blank`() = runTest {
        useCase(name = "", icon = Vault.Icon.Default)

        assertEquals(emptyList(), vaultRepository.observeVaults().first())
    }

    @Test
    fun `does not touch context when name is blank`() = runTest {
        useCase(name = "", icon = Vault.Icon.Default)

        assertEquals(VaultContext.NoSpecific, vaultContextRepository.currentContext)
        assertEquals(null, vaultContextRepository.currentLastInteracted)
    }

    @Test
    fun `creates vault on success`() = runTest {
        val result = useCase(name = "Personal", icon = Vault.Icon.Default)

        assertTrue(result.isSuccess())
        val stored = vaultRepository.observeVaults().first().single()
        assertEquals("Personal", stored.name)
        assertEquals(Vault.Icon.Default, stored.icon)
    }

    @Test
    fun `success returns the new vault id`() = runTest {
        val result = useCase(name = "Personal", icon = Vault.Icon.Default)

        val id = assertNotNull(result.getOrNull())
        val stored = vaultRepository.observeVaults().first().single()
        assertEquals(stored.id, id)
    }

    @Test
    fun `success persists wrapped vault key bytes`() = runTest {
        val result = useCase(name = "Personal", icon = Vault.Icon.Default)
        assertTrue(result.isSuccess())

        val stored = vaultRepository.observeVaults().first().single()
        assertTrue(stored.keyInformation.wrappedKey.isNotEmpty())
        assertTrue(stored.keyInformation.keyNonce.isNotEmpty())
    }

    @Test
    fun `success sets context and last interacted to the new vault`() = runTest {
        val result = useCase(name = "Personal", icon = Vault.Icon.Default)
        val id = assertNotNull(result.getOrNull())

        assertEquals(VaultContext.ById(id), vaultContextRepository.currentContext)
        assertEquals(id, vaultContextRepository.currentLastInteracted)
    }

    @Test
    fun `success preserves the chosen icon`() = runTest {
        val result = useCase(name = "Home", icon = Vault.Icon.Home)
        assertTrue(result.isSuccess())

        val stored = vaultRepository.observeVaults().first().single()
        assertEquals(Vault.Icon.Home, stored.icon)
    }

    @Test
    fun `creates distinct vault ids and keys across consecutive invocations`() = runTest {
        val a = assertNotNull(useCase(name = "A", icon = Vault.Icon.Default).getOrNull())
        val b = assertNotNull(useCase(name = "B", icon = Vault.Icon.Default).getOrNull())

        assertTrue(a != b)
        val all = vaultRepository.observeVaults().first()
        assertEquals(2, all.size)
        val keysA = all.single { it.id == a }.keyInformation.wrappedKey
        val keysB = all.single { it.id == b }.keyInformation.wrappedKey
        assertTrue(!keysA.contentEquals(keysB))
    }

    @Test
    fun `second create overrides context and last interacted with newer vault`() = runTest {
        val first = assertNotNull(useCase(name = "A", icon = Vault.Icon.Default).getOrNull())
        val second = assertNotNull(useCase(name = "B", icon = Vault.Icon.Default).getOrNull())

        assertTrue(first != second)
        assertEquals(VaultContext.ById(second), vaultContextRepository.currentContext)
        assertEquals(second, vaultContextRepository.currentLastInteracted)
    }
}
