package de.davis.keygo.feature.list_screen.domain.usecase

import de.davis.keygo.core.item.FakeVaultRepository
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Vault
import de.davis.keygo.feature.list_screen.FakeSelectedVaultRepository
import de.davis.keygo.feature.list_screen.domain.model.SelectedVault
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ObserveVaultsAndSelectionUseCaseTest {

    private val vaultA = testVault("Personal")
    private val vaultB = testVault("Work")

    private val vaultRepository = FakeVaultRepository()
    private val selectedVaultRepository = FakeSelectedVaultRepository()
    private val useCase = ObserveVaultsAndSelectionUseCase(
        vaultRepository = vaultRepository,
        selectedVaultRepository = selectedVaultRepository,
    )

    @Test
    fun `emits empty vaults and All when no vaults seeded and no selection set`() = runTest {
        val result = useCase().first()

        assertEquals(emptyList(), result.vaults)
        assertEquals(SelectedVault.All, result.selection)
    }

    @Test
    fun `emits seeded vaults with All when selection is All`() = runTest {
        vaultRepository.seed(vaultA, vaultB)

        val result = useCase().first()

        assertEquals(setOf(vaultA.id, vaultB.id), result.vaults.map { it.vaultId }.toSet())
        assertEquals(SelectedVault.All, result.selection)
    }

    @Test
    fun `emits selection set via SelectedVaultRepository`() = runTest {
        vaultRepository.seed(vaultA)
        selectedVaultRepository.set(SelectedVault.Id(vaultA.id))

        val result = useCase().first()

        val selection = assertIs<SelectedVault.Id>(result.selection)
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
