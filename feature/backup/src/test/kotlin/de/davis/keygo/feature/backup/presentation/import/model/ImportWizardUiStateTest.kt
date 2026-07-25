package de.davis.keygo.feature.backup.presentation.import.model

import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.Vault
import de.davis.keygo.feature.backup.domain.model.BackupDestination
import de.davis.keygo.feature.backup.domain.model.ImportTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImportWizardUiStateTest {

    private fun destination(fileName: String?) = BackupDestination(
        provider = BackupDestination.Provider.OnDevice,
        displayPath = "Internal storage/Backups",
        fileName = fileName,
    )

    @Test
    fun `canContinue on SelectVault is false when creating a new vault with an invalid name`() {
        val state = ImportWizardUiState(
            step = ImportWizardStep.SelectVault,
            creatingNewVault = true,
            newVaultNameValid = false,
        )

        assertFalse(state.canContinue)
    }

    @Test
    fun `canContinue on SelectVault is true when creating a new vault with a valid name`() {
        val state = ImportWizardUiState(
            step = ImportWizardStep.SelectVault,
            creatingNewVault = true,
            newVaultNameValid = true,
        )

        assertTrue(state.canContinue)
    }

    @Test
    fun `canContinue on SelectVault is false when no existing vault is selected`() {
        val state = ImportWizardUiState(
            step = ImportWizardStep.SelectVault,
            creatingNewVault = false,
            selectedVaultId = null,
        )

        assertFalse(state.canContinue)
    }

    @Test
    fun `canContinue on SelectVault is true when an existing vault is selected`() {
        val state = ImportWizardUiState(
            step = ImportWizardStep.SelectVault,
            creatingNewVault = false,
            selectedVaultId = newVaultId(),
        )

        assertTrue(state.canContinue)
    }

    @Test
    fun `resolveTarget carries the chosen icon into the new vault`() {
        val state = ImportWizardUiState(
            creatingNewVault = true,
            newVaultIcon = Vault.Icon.ShoppingCart,
        )

        assertEquals(
            ImportTarget.New("passwords", Vault.Icon.ShoppingCart),
            state.resolveTarget("passwords"),
        )
    }

    @Test
    fun `resolveTarget ignores the chosen icon when an existing vault is the target`() {
        val vaultId = newVaultId()
        val state = ImportWizardUiState(
            creatingNewVault = false,
            selectedVaultId = vaultId,
            newVaultIcon = Vault.Icon.ShoppingCart,
        )

        assertEquals(ImportTarget.Existing(vaultId), state.resolveTarget("passwords"))
    }

    @Test
    fun `suggestedVaultName strips the extension`() {
        val state = ImportWizardUiState(backupDestination = destination("passwords.csv"))

        assertEquals("passwords", state.suggestedVaultName)
    }

    @Test
    fun `suggestedVaultName is the file name unchanged when there is no extension`() {
        val state = ImportWizardUiState(backupDestination = destination("passwords"))

        assertEquals("passwords", state.suggestedVaultName)
    }

    @Test
    fun `suggestedVaultName keeps everything before the last dot when there are several`() {
        val state = ImportWizardUiState(backupDestination = destination("my.passwords.backup.csv"))

        assertEquals("my.passwords.backup", state.suggestedVaultName)
    }
}
