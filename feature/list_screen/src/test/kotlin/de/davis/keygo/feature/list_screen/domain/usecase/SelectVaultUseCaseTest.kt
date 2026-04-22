package de.davis.keygo.feature.list_screen.domain.usecase

import de.davis.keygo.core.item.FakeVaultRepository
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Vault
import de.davis.keygo.feature.list_screen.FakeSelectedVaultRepository
import de.davis.keygo.feature.list_screen.domain.model.SelectedVault
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SelectVaultUseCaseTest {

    private val vaultA = Vault(
        id = newVaultId(),
        name = "Personal",
        keyInformation = KeyInformation(byteArrayOf(), byteArrayOf()),
        icon = Vault.Icon.Default,
    )
    private val vaultB = Vault(
        id = newVaultId(),
        name = "Work",
        keyInformation = KeyInformation(byteArrayOf(), byteArrayOf()),
        icon = Vault.Icon.Default,
    )

    private val vaultRepository = FakeVaultRepository()
    private val selectedVaultRepository = FakeSelectedVaultRepository()
    private val useCase = SelectVaultUseCase(
        vaultRepository = vaultRepository,
        selectedVaultRepository = selectedVaultRepository,
    )

    @BeforeTest
    fun seedVaults() = runTest {
        // seed() activates the first vault, so active starts pointing at vaultA
        vaultRepository.seed(vaultA, vaultB)
    }

    @Test
    fun `selecting an Id persists the selection`() = runTest {
        useCase(SelectedVault.Id(vaultB.id))

        assertEquals(SelectedVault.Id(vaultB.id), selectedVaultRepository.current)
    }

    @Test
    fun `selecting an Id updates the active vault`() = runTest {
        useCase(SelectedVault.Id(vaultB.id))

        assertEquals(vaultB.id, vaultRepository.observeActiveVaultId().first())
    }

    @Test
    fun `selecting All persists the selection`() = runTest {
        useCase(SelectedVault.Id(vaultA.id))

        useCase(SelectedVault.All)

        assertEquals(SelectedVault.All, selectedVaultRepository.current)
    }

    @Test
    fun `selecting All leaves the active vault unchanged`() = runTest {
        // Precondition: active vault points at vaultA after seed().
        useCase(SelectedVault.All)

        assertEquals(vaultA.id, vaultRepository.observeActiveVaultId().first())
    }

    @Test
    fun `re-selecting an Id after All updates active vault again`() = runTest {
        useCase(SelectedVault.All)

        useCase(SelectedVault.Id(vaultB.id))

        assertEquals(vaultB.id, vaultRepository.observeActiveVaultId().first())
        assertEquals(SelectedVault.Id(vaultB.id), selectedVaultRepository.current)
    }
}
