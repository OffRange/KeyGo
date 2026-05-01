package de.davis.keygo.feature.vault.domain.usecase

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
import kotlin.test.assertIs

class ObserveVaultsAndSelectionUseCaseTest {

    private val vaultA = testVault("Personal")
    private val vaultB = testVault("Work")

    private val vaultRepository = FakeVaultRepository()
    private val vaultContextRepository = FakeVaultContextRepository()
    private val useCase = ObserveVaultsAndSelectionUseCase(
        vaultRepository = vaultRepository,
        vaultContextRepository = vaultContextRepository,
    )

    @Test
    fun `emits empty vaults and NoSpecific when no vaults seeded and no context set`() = runTest {
        val result = useCase().first()

        assertEquals(emptyList(), result.vaults)
        assertEquals(VaultContext.NoSpecific, result.selection)
    }

    @Test
    fun `emits seeded vaults with NoSpecific when context is NoSpecific`() = runTest {
        vaultRepository.seed(vaultA, vaultB)

        val result = useCase().first()

        assertEquals(setOf(vaultA.id, vaultB.id), result.vaults.map { it.vaultId }.toSet())
        assertEquals(VaultContext.NoSpecific, result.selection)
    }

    @Test
    fun `emits context set via VaultContextRepository`() = runTest {
        vaultRepository.seed(vaultA)
        vaultContextRepository.setVaultContext(VaultContext.ById(vaultA.id))

        val result = useCase().first()

        val selection = assertIs<VaultContext.ById>(result.selection)
        assertEquals(vaultA.id, selection.vaultId)
    }

    @Test
    fun `maps vaults to metadata with name and icon`() = runTest {
        vaultRepository.seed(vaultA)

        val metadata = useCase().first().vaults.single()

        assertEquals(vaultA.id, metadata.vaultId)
        assertEquals(vaultA.name, metadata.name)
        assertEquals(vaultA.icon, metadata.icon)
    }

    @Test
    fun `sorts vaults by name`() = runTest {
        val zebra = testVault("Zebra")
        val alpha = testVault("Alpha")
        val mango = testVault("Mango")
        vaultRepository.seed(zebra, alpha, mango)

        val names = useCase().first().vaults.map { it.name }

        assertEquals(listOf("Alpha", "Mango", "Zebra"), names)
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
