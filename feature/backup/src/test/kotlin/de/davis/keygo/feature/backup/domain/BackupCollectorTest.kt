package de.davis.keygo.feature.backup.domain

import de.davis.keygo.core.item.FakeCreditCardRepository
import de.davis.keygo.core.item.FakeItemRepository
import de.davis.keygo.core.item.FakeLoginRepository
import de.davis.keygo.core.item.FakePasskeyRepository
import de.davis.keygo.core.item.FakeVaultRepository
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.domain.model.Vault
import de.davis.keygo.core.security.crypto.FakeCryptographicScopeProvider
import de.davis.keygo.core.security.crypto.FakeCryptographicScopeProviderFactory
import de.davis.keygo.core.security.crypto.FakeKeyStoreManager
import de.davis.keygo.core.security.crypto.FakeSession
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.getOrNull
import de.davis.keygo.feature.backup.FakeBackupArkKeyStore
import de.davis.keygo.feature.backup.domain.model.CollectedBackup
import de.davis.keygo.feature.backup.domain.model.ExportError
import de.davis.keygo.feature.backup.testCard
import de.davis.keygo.feature.backup.testLogin
import de.davis.keygo.feature.backup.testPasskey
import de.davis.keygo.feature.backup.testVault
import kotlinx.coroutines.test.runTest
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BackupCollectorTest {

    private val vaultRepo = FakeVaultRepository()
    private val loginRepo = FakeLoginRepository()
    private val cardRepo = FakeCreditCardRepository()
    private val passkeyRepo = FakePasskeyRepository()
    private val factory = FakeCryptographicScopeProviderFactory(
        FakeCryptographicScopeProvider(FakeItemRepository()),
    )

    private fun collector(
        session: FakeSession = FakeSession(startOnConstruct = true),
        unlockerVaultRepo: FakeVaultRepository = vaultRepo,
    ) = BackupCollector(
        vaultRepository = vaultRepo,
        loginRepository = loginRepo,
        creditCardRepository = cardRepo,
        passkeyRepository = passkeyRepo,
        arkUnlocker = BackupArkUnlocker(
            session = session,
            keyStoreManager = FakeKeyStoreManager(),
            arkKeyStore = FakeBackupArkKeyStore(),
            scopeProviderFactory = factory,
            vaultRepository = unlockerVaultRepo,
        ),
    )

    @Test
    fun `empty database fails with NothingToExport`() = runTest {
        val result = collector().collect { _, _ -> }
        assertIs<Result.Failure<*, ExportError>>(result)
        assertEquals(ExportError.NothingToExport, result.error)
    }

    @Test
    fun `collects and decrypts a login into the backup`() = runTest {
        val vault = testVault(name = "Personal")
        vaultRepo.seed(vault)
        loginRepo.seed(
            testLogin(
                vaultId = vault.id,
                name = "Email",
                username = "alice",
                password = "s3cr3t",
                totpSecret = "JBSWY3DPEHPK3PXP",
                websites = setOf("https://mail.example"),
                tags = setOf("work"),
                note = "remember",
            )
        )

        val result = collector().collect { _, _ -> }
        val collected: CollectedBackup = assertNotNull(result.getOrNull())
        val login = collected.backup.vaults.single().logins.single()
        assertEquals("Email", login.title)
        assertEquals("alice", login.username)
        assertEquals("s3cr3t", login.password)
        assertEquals("JBSWY3DPEHPK3PXP", login.totpSecret)
        assertEquals(listOf("https://mail.example"), login.websites)
        assertEquals(listOf("work"), login.tags)
        assertEquals("remember", login.notes)
        assertEquals(1, collected.itemCount)
    }

    @Test
    fun `a login with multiple websites exports all of them`() = runTest {
        val vault = testVault(name = "Personal")
        vaultRepo.seed(vault)
        loginRepo.seed(
            testLogin(
                vaultId = vault.id,
                name = "Email",
                websites = setOf("https://mail.example", "https://mail.example.org"),
            )
        )

        val result = collector().collect { _, _ -> }
        val login = assertNotNull(result.getOrNull()).backup.vaults.single().logins.single()

        assertEquals(
            setOf("https://mail.example", "https://mail.example.org"),
            login.websites.toSet(),
        )
    }

    @Test
    fun `collects and decrypts a card into the backup`() = runTest {
        val vault = testVault(name = "Wallet")
        vaultRepo.seed(vault)
        cardRepo.seed(
            testCard(
                vaultId = vault.id,
                name = "Visa",
                holder = "Alice",
                number = "4111111111111111",
                cvv = "123",
                expiration = YearMonth.of(2030, 7),
                note = "card note",
            )
        )

        val result = collector().collect { _, _ -> }
        val collected: CollectedBackup = assertNotNull(result.getOrNull())
        val card = collected.backup.vaults.single().cards.single()
        assertEquals("Visa", card.title)
        assertEquals("Alice", card.cardholder)
        assertEquals("4111111111111111", card.number)
        assertEquals("123", card.cvv)
        assertEquals(7u.toUByte(), card.expirationMonth)
        assertEquals(2030u.toUShort(), card.expirationYear)
        assertEquals("card note", card.notes)
    }

    @Test
    fun `keeps items grouped by their vault`() = runTest {
        val a = testVault(name = "A")
        val b = testVault(name = "B")
        vaultRepo.seed(a, b)
        loginRepo.seed(testLogin(vaultId = a.id, name = "InA"))
        cardRepo.seed(testCard(vaultId = b.id, name = "InB", number = "4111111111111111"))

        val collected: CollectedBackup =
            assertNotNull(collector().collect { _, _ -> }.getOrNull())
        val byName = collected.backup.vaults.associateBy { it.name }

        assertEquals(listOf("InA"), byName.getValue("A").logins.map { it.title })
        assertEquals(listOf("InB"), byName.getValue("B").cards.map { it.title })
        assertEquals(2, collected.itemCount)
    }

    @Test
    fun `exports each vault's icon under its enum name`() = runTest {
        val work = testVault(name = "Work", icon = Vault.Icon.Business)
        val personal = testVault(name = "Personal", icon = Vault.Icon.Home)
        vaultRepo.seed(work, personal)
        loginRepo.seed(testLogin(vaultId = work.id, name = "InWork"))
        loginRepo.seed(testLogin(vaultId = personal.id, name = "InPersonal"))

        val collected: CollectedBackup =
            assertNotNull(collector().collect { _, _ -> }.getOrNull())

        assertEquals(
            mapOf("Work" to "Business", "Personal" to "Home"),
            collected.backup.vaults.associate { it.name to it.icon },
        )
    }

    @Test
    fun `reports progress up to the total`() = runTest {
        val vault = testVault(name = "V")
        vaultRepo.seed(vault)
        loginRepo.seed(testLogin(vaultId = vault.id, name = "L1"), testLogin(vaultId = vault.id, name = "L2"))

        val seen = mutableListOf<Pair<Int, Int>>()
        val result = collector().collect { processed, total -> seen += processed to total }

        assertIs<Result.Success<*, *>>(result)
        assertEquals(listOf(1 to 2, 2 to 2), seen)
    }

    @Test
    fun `crypto scope failure surfaces CryptoFailed`() = runTest {
        val vault = testVault(name = "V")
        vaultRepo.seed(vault)
        loginRepo.seed(testLogin(vaultId = vault.id, name = "Email"))

        // The crypto-scope use case is given an empty vault repo, so it cannot find the
        // vault key and fails to build a scope - the collector maps any such failure to CryptoFailed.
        val result = collector(unlockerVaultRepo = FakeVaultRepository()).collect { _, _ -> }

        assertIs<Result.Failure<*, ExportError>>(result)
        assertEquals(ExportError.CryptoFailed, result.error)
    }

    @Test
    fun `locked and unprovisioned session fails with NotProvisioned before reporting progress`() =
        runTest {
            val vault = testVault(name = "V")
            vaultRepo.seed(vault)
            loginRepo.seed(testLogin(vaultId = vault.id, name = "Email"))

            val seen = mutableListOf<Pair<Int, Int>>()
            val result = collector(session = FakeSession(startOnConstruct = false))
                .collect { processed, total -> seen += processed to total }

            assertEquals(Result.Failure(ExportError.NotProvisioned), result)
            assertTrue(seen.isEmpty())
        }

    @Test
    fun `a login's passkeys are collected and decrypted`() = runTest {
        val vault = testVault(name = "Personal")
        vaultRepo.seed(vault)
        val loginId = newItemId()
        loginRepo.seed(
            testLogin(
                vaultId = vault.id,
                id = loginId,
                name = "Email",
                passkeyRPs = setOf("example.com", "example.org"),
            )
        )
        passkeyRepo.seed(
            testPasskey(loginId = loginId, rp = "example.com", privateKey = "pk-one"),
            testPasskey(loginId = loginId, rp = "example.org", privateKey = "pk-two"),
        )

        val result = collector().collect { _, _ -> }
        val login = assertNotNull(result.getOrNull()).backup.vaults.single().logins.single()

        assertEquals(listOf("example.com", "example.org"), login.passkeys.map { it.rp })
        assertEquals("pk-one", login.passkeys.first().privateKey.decodeToString())
        assertEquals("alice", login.passkeys.first().userName)
    }

    @Test
    fun `a login without passkeys exports an empty list`() = runTest {
        val vault = testVault(name = "Personal")
        vaultRepo.seed(vault)
        loginRepo.seed(testLogin(vaultId = vault.id, name = "Email", password = "s3cr3t"))

        val result = collector().collect { _, _ -> }
        val login = assertNotNull(result.getOrNull()).backup.vaults.single().logins.single()

        assertTrue(login.passkeys.isEmpty())
    }

    @Test
    fun `passkeys are exported even when the login's passkeyRPs set is empty`() = runTest {
        val vault = testVault(name = "Personal")
        vaultRepo.seed(vault)
        val loginId = newItemId()
        // passkeyRPs deliberately left empty (default) - the table is the source of truth.
        loginRepo.seed(testLogin(vaultId = vault.id, id = loginId, name = "Email"))
        passkeyRepo.seed(testPasskey(loginId = loginId, rp = "example.com", privateKey = "pk-one"))

        val result = collector().collect { _, _ -> }
        val login = assertNotNull(result.getOrNull()).backup.vaults.single().logins.single()

        assertEquals(listOf("example.com"), login.passkeys.map { it.rp })
    }
}
