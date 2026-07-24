package de.davis.keygo.feature.backup.domain

import de.davis.keygo.core.item.FakeLoginRepository
import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.backup.RestorerTestEnv
import de.davis.keygo.feature.backup.domain.model.ImportTarget
import de.davis.keygo.feature.backup.testLogin
import de.davis.keygo.feature.backup.testVault
import de.davisalessandro.keygo.rust.Backup
import de.davisalessandro.keygo.rust.BackupCard
import de.davisalessandro.keygo.rust.BackupLogin
import de.davisalessandro.keygo.rust.BackupVault
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BackupRestorerTest {

    private fun login(title: String, username: String? = null) = BackupLogin(
        title = title,
        notes = null,
        tags = emptyList(),
        pinned = false,
        username = username,
        password = "pw",
        totpSecret = null,
        website = null,
        passkeys = emptyList(),
    )

    private fun backup(vararg vaults: BackupVault) = Backup(vaults.toList())
    private fun vault(name: String, logins: List<BackupLogin>) =
        BackupVault(name = name, logins = logins, cards = emptyList())

    @Test
    fun `empty backup fails with NothingImported`() = runTest {
        val env = RestorerTestEnv()
        val result = env.restorer.restore(Backup(emptyList())) { _, _ -> }
        assertIs<Result.Failure<*, *>>(result)
    }

    @Test
    fun `creates a new vault and imports its logins`() = runTest {
        val env = RestorerTestEnv()
        val result = env.restorer.restore(
            backup(vault("Imported", listOf(login("Email", "alice"), login("Bank", "bob")))),
        ) { _, _ -> }

        val summary = (result as Result.Success).success
        assertEquals(2, summary.imported)
        assertEquals(1, summary.vaultsCreated)
        assertEquals(2, env.loginRepo.observeLoginsCount())
    }

    @Test
    fun `reuses an existing vault by name`() = runTest {
        val env = RestorerTestEnv()
        env.vaultRepo.seed(testVault(name = "Personal"))

        val summary = (env.restorer.restore(
            backup(vault("Personal", listOf(login("Email", "alice")))),
        ) { _, _ -> } as Result.Success).success

        assertEquals(0, summary.vaultsCreated)
        assertEquals(1, summary.imported)
    }

    @Test
    fun `skips a login that already exists by name and username`() = runTest {
        val env = RestorerTestEnv()
        val existing = testVault(name = "Personal")
        env.vaultRepo.seed(existing)
        env.loginRepo.seed(testLogin(vaultId = existing.id, name = "Email", username = "alice"))

        val summary = (env.restorer.restore(
            backup(vault("Personal", listOf(login("Email", "alice"), login("New", "carol")))),
        ) { _, _ -> } as Result.Success).success

        assertEquals(1, summary.imported)
        assertEquals(1, summary.skipped)
    }

    @Test
    fun `reports progress for every item`() = runTest {
        val env = RestorerTestEnv()
        val seen = mutableListOf<Pair<Int, Int>>()
        env.restorer.restore(
            backup(vault("V", listOf(login("A"), login("B")))),
        ) { p, t -> seen += p to t }
        assertEquals(listOf(1 to 2, 2 to 2), seen)
    }

    @Test
    fun `vault creation failure marks items failed and reports progress per item`() = runTest {
        // A blank vault name makes CreateVaultUseCase fail, so the vault cannot be created.
        val env = RestorerTestEnv()
        val seen = mutableListOf<Pair<Int, Int>>()
        val summary = (env.restorer.restore(
            backup(vault("", listOf(login("A"), login("B")))),
        ) { p, t -> seen += p to t } as Result.Success).success

        assertEquals(0, summary.imported)
        assertEquals(0, summary.vaultsCreated)
        assertEquals(2, summary.failed)
        assertEquals(listOf(1 to 2, 2 to 2), seen)
    }

    @Test
    fun `wraps the whole restore in a single transaction`() = runTest {
        val env = RestorerTestEnv()
        env.restorer.restore(
            backup(
                vault("V1", listOf(login("A"), login("B"))),
                vault("V2", listOf(login("C"))),
            ),
        ) { _, _ -> }

        assertEquals(1, env.transactionRunner.enteredCount)
    }

    @Test
    fun `Existing target routes every item into that vault`() = runTest {
        val env = RestorerTestEnv()
        val existing = testVault(name = "Personal")
        env.vaultRepo.seed(existing)

        val summary = (env.restorer.restore(
            backup(vault("CSV Import", listOf(login("Email", "alice"), login("Bank", "bob")))),
            ImportTarget.Existing(existing.id),
        ) { _, _ -> } as Result.Success).success

        assertEquals(2, summary.imported)
        assertEquals(0, summary.vaultsCreated)
        assertEquals(2, env.loginRepo.getLoginsByVault(existing.id).size)
    }

    @Test
    fun `New target creates exactly one vault for the whole backup`() = runTest {
        val env = RestorerTestEnv()

        val summary = (env.restorer.restore(
            backup(
                vault("First", listOf(login("Email", "alice"))),
                vault("Second", listOf(login("Bank", "bob"))),
            ),
            ImportTarget.New("passwords"),
        ) { _, _ -> } as Result.Success).success

        assertEquals(2, summary.imported)
        assertEquals(1, summary.vaultsCreated)
        assertEquals(
            listOf("passwords"),
            env.vaultRepo.observeAllVaultMetadata().first().map { it.name },
        )
    }

    @Test
    fun `New target creates a vault even when one of that name already exists`() = runTest {
        val env = RestorerTestEnv()
        env.vaultRepo.seed(testVault(name = "passwords"))

        val summary = (env.restorer.restore(
            backup(vault("CSV Import", listOf(login("Email", "alice")))),
            ImportTarget.New("passwords"),
        ) { _, _ -> } as Result.Success).success

        assertEquals(1, summary.vaultsCreated)
        assertEquals(2, env.vaultRepo.observeAllVaultMetadata().first().size)
    }

    @Test
    fun `a target ignores the vault names carried by the backup`() = runTest {
        val env = RestorerTestEnv()
        val existing = testVault(name = "Personal")
        env.vaultRepo.seed(existing)

        env.restorer.restore(
            backup(vault("CSV Import", listOf(login("Email", "alice")))),
            ImportTarget.Existing(existing.id),
        ) { _, _ -> }

        assertEquals(
            listOf("Personal"),
            env.vaultRepo.observeAllVaultMetadata().first().map { it.name },
        )
    }

    // The created vault must join the same transaction as the items, so a rollback takes both.
    // FakeTransactionRunner deliberately does not model rollback (it documents this), so asserting
    // "the vault disappears on failure" against it would pass vacuously. What is real at this layer
    // is that exactly one transaction wraps vault creation *and* every item write.
    @Test
    fun `New target creates its vault inside the single import transaction`() = runTest {
        val env = RestorerTestEnv()

        env.restorer.restore(
            backup(vault("First", listOf(login("Email", "alice"), login("Bank", "bob")))),
            ImportTarget.New("passwords"),
        ) { _, _ -> }

        assertEquals(1, env.transactionRunner.enteredCount)
    }
}

private suspend fun FakeLoginRepository.observeLoginsCount(): Int =
    observeLogins().first().size
