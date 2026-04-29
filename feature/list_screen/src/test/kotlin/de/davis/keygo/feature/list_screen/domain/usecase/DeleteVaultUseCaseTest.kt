package de.davis.keygo.feature.list_screen.domain.usecase

import de.davis.keygo.core.item.FakeVaultContextRepository
import de.davis.keygo.core.item.FakeVaultRepository
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Vault
import de.davis.keygo.core.item.domain.model.VaultContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DeleteVaultUseCaseTest {

    private val vaultA = testVault("Alpha")
    private val vaultB = testVault("Bravo")
    private val vaultC = testVault("Charlie")

    private val vaultRepository = FakeVaultRepository()
    private val vaultContextRepository = FakeVaultContextRepository()

    private val useCase = DeleteVaultUseCase(
        vaultRepository = vaultRepository,
        vaultContextRepository = vaultContextRepository,
    )

    @Test
    fun `removes the deleted vault from the repository`() = runTest {
        vaultRepository.seed(vaultA, vaultB)
        vaultContextRepository.seedLastInteracted(vaultB.id)

        useCase(vaultA.id)

        val remainingIds = vaultRepository.observeVaults().first().map { it.id }
        assertEquals(listOf(vaultB.id), remainingIds)
    }

    @Test
    fun `clears context to NoSpecific and keeps last interacted when deleting the current vault`() = runTest {
        vaultRepository.seed(vaultA, vaultB)
        vaultContextRepository.seedLastInteracted(vaultB.id)
        vaultContextRepository.setVaultContext(VaultContext.ById(vaultA.id))

        useCase(vaultA.id)

        assertEquals(VaultContext.NoSpecific, vaultContextRepository.currentContext)
        assertEquals(vaultB.id, vaultContextRepository.currentLastInteracted)
    }

    @Test
    fun `keeps context unchanged when deleting a non-current vault`() = runTest {
        vaultRepository.seed(vaultA, vaultB)
        vaultContextRepository.seedLastInteracted(vaultB.id)
        vaultContextRepository.setVaultContext(VaultContext.ById(vaultB.id))

        useCase(vaultA.id)

        assertEquals(VaultContext.ById(vaultB.id), vaultContextRepository.currentContext)
        assertEquals(vaultB.id, vaultContextRepository.currentLastInteracted)
    }

    @Test
    fun `keeps NoSpecific context unchanged when context was already NoSpecific`() = runTest {
        vaultRepository.seed(vaultA, vaultB)
        vaultContextRepository.seedLastInteracted(vaultB.id)

        useCase(vaultA.id)

        assertEquals(VaultContext.NoSpecific, vaultContextRepository.currentContext)
    }

    @Test
    fun `switches last interacted to the most recent remaining vault when deleting the last interacted one`() =
        runTest {
            vaultRepository.seed(vaultA, vaultB, vaultC)
            vaultContextRepository.seedLastInteracted(vaultC.id)

            useCase(vaultC.id)

            assertEquals(vaultB.id, vaultContextRepository.currentLastInteracted)
        }

    @Test
    fun `keeps last interacted unchanged when deleting a different vault`() = runTest {
        vaultRepository.seed(vaultA, vaultB)
        vaultContextRepository.seedLastInteracted(vaultB.id)

        useCase(vaultA.id)

        assertEquals(vaultB.id, vaultContextRepository.currentLastInteracted)
    }

    @Test
    fun `clears context and reroutes last interacted when deleting a vault that is both current and last interacted`() =
        runTest {
            vaultRepository.seed(vaultA, vaultB)
            vaultContextRepository.seedLastInteracted(vaultB.id)
            vaultContextRepository.setVaultContext(VaultContext.ById(vaultB.id))

            useCase(vaultB.id)

            assertEquals(VaultContext.NoSpecific, vaultContextRepository.currentContext)
            assertEquals(vaultA.id, vaultContextRepository.currentLastInteracted)
        }

    @Test
    fun `updates last interacted but keeps context when deleting last interacted that is not the current vault`() =
        runTest {
            vaultRepository.seed(vaultA, vaultB, vaultC)
            vaultContextRepository.seedLastInteracted(vaultC.id)
            vaultContextRepository.setVaultContext(VaultContext.ById(vaultA.id))

            useCase(vaultC.id)

            assertEquals(VaultContext.ById(vaultA.id), vaultContextRepository.currentContext)
            assertEquals(vaultB.id, vaultContextRepository.currentLastInteracted)
        }

    @Test
    fun `clears context and keeps last interacted when deleting the only vault while it is the current context`() =
        runTest {
            vaultRepository.seed(vaultA)
            vaultContextRepository.seedLastInteracted(vaultA.id)
            vaultContextRepository.setVaultContext(VaultContext.ById(vaultA.id))

            useCase(vaultA.id)

            assertEquals(VaultContext.NoSpecific, vaultContextRepository.currentContext)
            assertEquals(vaultA.id, vaultContextRepository.currentLastInteracted)
        }

    private fun testVault(
        name: String,
        id: VaultId = newVaultId(),
    ) = Vault(
        id = id,
        name = name,
        keyInformation = KeyInformation(byteArrayOf(), byteArrayOf()),
        icon = Vault.Icon.Default,
    )
}
