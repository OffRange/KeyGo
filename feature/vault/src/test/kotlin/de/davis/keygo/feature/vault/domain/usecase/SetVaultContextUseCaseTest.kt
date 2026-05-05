package de.davis.keygo.feature.vault.domain.usecase

import de.davis.keygo.core.item.FakeVaultContextRepository
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.VaultContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SetVaultContextUseCaseTest {

    private val vaultIdA = newVaultId()
    private val vaultIdB = newVaultId()

    private val vaultContextRepository = FakeVaultContextRepository()
    private val useCase = SetVaultContextUseCase(vaultContextRepository)

    @Test
    fun `setting ById persists context`() = runTest {
        useCase(VaultContext.ById(vaultIdA))

        assertEquals(VaultContext.ById(vaultIdA), vaultContextRepository.observeVaultContext().first())
    }

    @Test
    fun `setting ById updates last interacted vault`() = runTest {
        useCase(VaultContext.ById(vaultIdA))

        assertEquals(vaultIdA, vaultContextRepository.currentLastInteracted)
    }

    @Test
    fun `setting NoSpecific persists context`() = runTest {
        useCase(VaultContext.NoSpecific)

        assertEquals(VaultContext.NoSpecific, vaultContextRepository.observeVaultContext().first())
    }

    @Test
    fun `setting NoSpecific does not update last interacted vault`() = runTest {
        useCase(VaultContext.NoSpecific)

        assertNull(vaultContextRepository.currentLastInteracted)
    }

    @Test
    fun `setting NoSpecific after ById preserves last interacted vault`() = runTest {
        useCase(VaultContext.ById(vaultIdA))

        useCase(VaultContext.NoSpecific)

        assertEquals(vaultIdA, vaultContextRepository.currentLastInteracted)
        assertEquals(VaultContext.NoSpecific, vaultContextRepository.observeVaultContext().first())
    }

    @Test
    fun `re-selecting a different ById updates both context and last interacted`() = runTest {
        useCase(VaultContext.ById(vaultIdA))

        useCase(VaultContext.ById(vaultIdB))

        assertEquals(VaultContext.ById(vaultIdB), vaultContextRepository.observeVaultContext().first())
        assertEquals(vaultIdB, vaultContextRepository.currentLastInteracted)
    }
}
