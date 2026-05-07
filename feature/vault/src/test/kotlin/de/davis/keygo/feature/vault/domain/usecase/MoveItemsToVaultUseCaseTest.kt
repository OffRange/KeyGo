package de.davis.keygo.feature.vault.domain.usecase

import de.davis.keygo.core.item.FakeItemRepository
import de.davis.keygo.core.item.FakePasswordRepository
import de.davis.keygo.core.item.FakeVaultRepository
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.DomainInfo
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Login
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.item.domain.model.Totp
import de.davis.keygo.core.item.domain.model.Vault
import de.davis.keygo.core.security.crypto.BindingCryptographicScopeProvider
import de.davis.keygo.core.security.crypto.FakeSession
import de.davis.keygo.core.security.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.security.domain.crypto.decryptSecretData
import de.davis.keygo.core.security.domain.crypto.encryptSecretData
import de.davis.keygo.core.security.domain.crypto.model.WrappedItemKeyInformation
import de.davis.keygo.core.security.domain.crypto.model.WrappedVaultKeyInformation
import de.davis.keygo.core.security.domain.crypto.wrappedItemKeyInformation
import de.davis.keygo.core.util.isFailure
import de.davis.keygo.core.util.isSuccess
import de.davis.keygo.feature.vault.domain.model.MoveItemsError
import de.davis.keygo.feature.vault.domain.model.MoveItemsProgress
import de.davis.keygo.rust.FakeItemManager
import de.davis.keygo.rust.FakeKeyWrapper
import de.davisalessandro.keygo.rust.ItemAad
import de.davisalessandro.keygo.rust.KeyWrapException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MoveItemsToVaultUseCaseTest {

    private val session = FakeSession()
    private val itemManager = FakeItemManager()
    private val keyWrapper = FakeKeyWrapper()
    private val cryptographicScopeProvider: CryptographicScopeProvider =
        BindingCryptographicScopeProvider(session, itemManager, keyWrapper)

    private val passwordRepository = FakePasswordRepository()
    private val itemRepository = FakeItemRepository(passwordRepository)
    private val vaultRepository = FakeVaultRepository()

    private val useCase = MoveItemsToVaultUseCase(
        cryptographicScopeProvider = cryptographicScopeProvider,
        itemRepository = itemRepository,
        vaultRepository = vaultRepository,
    )

    private val srcVault = makeVault("Source")
    private val dstVault = makeVault("Destination")

    @Test
    fun `moved password and totp decrypt under destination vault`() = runTest {
        seedVaults(srcVault, dstVault)
        val passwordPlaintext = "hunter2"
        val totpPlaintext = "JBSWY3DPEHPK3PXP"
        val seeded = encryptAndSeed(
            srcVault,
            passwordPlaintext = passwordPlaintext,
            totpPlaintext = totpPlaintext,
        )

        val result = useCase(srcVault.id, dstVault.id)

        assertTrue(result.isSuccess())
        val moved = passwordRepository.getPasswordById(seeded.id)
        assertNotNull(moved)
        assertEquals(dstVault.id, moved.vaultId)
        val (recoveredPassword, recoveredTotp) = decrypt(moved, dstVault)
        assertEquals(passwordPlaintext, recoveredPassword)
        assertEquals(totpPlaintext, recoveredTotp)
    }

    @Test
    fun `moved item gets a fresh wrapped key`() = runTest {
        seedVaults(srcVault, dstVault)
        val seeded = encryptAndSeed(srcVault, passwordPlaintext = "x")

        useCase(srcVault.id, dstVault.id)

        val moved = passwordRepository.getPasswordById(seeded.id)!!
        assertNotEquals(
            seeded.keyInformation.wrappedKey.toList(),
            moved.keyInformation.wrappedKey.toList(),
        )
    }

    @Test
    fun `null totp survives the move as null`() = runTest {
        seedVaults(srcVault, dstVault)
        val seeded = encryptAndSeed(srcVault, passwordPlaintext = "x", totpPlaintext = null)

        useCase(srcVault.id, dstVault.id)

        val moved = passwordRepository.getPasswordById(seeded.id)!!
        assertNull(moved.totp)
    }

    @Test
    fun `plaintext fields are unchanged through the move`() = runTest {
        seedVaults(srcVault, dstVault)
        val seeded = encryptAndSeed(
            srcVault,
            name = "Acme",
            username = "alice",
            note = "shared corp account",
            passwordScore = PasswordScore.Strong,
            domainInfos = setOf(DomainInfo(value = "acme.com", eTLD1 = "acme.com")),
            pinned = true,
            passwordPlaintext = "x",
        )

        useCase(srcVault.id, dstVault.id)

        val moved = passwordRepository.getPasswordById(seeded.id)!!
        assertEquals(seeded.name, moved.name)
        assertEquals(seeded.username, moved.username)
        assertEquals(seeded.note, moved.note)
        assertEquals(seeded.passwordScore, moved.passwordScore)
        assertEquals(seeded.domainInfos, moved.domainInfos)
        assertEquals(seeded.pinned, moved.pinned)
    }

    @Test
    fun `same source and destination is a no-op success`() = runTest {
        seedVaults(srcVault)
        val seeded = encryptAndSeed(srcVault, passwordPlaintext = "x")

        val result = useCase(srcVault.id, srcVault.id)

        assertTrue(result.isSuccess())
        val after = passwordRepository.getPasswordById(seeded.id)!!
        assertEquals(srcVault.id, after.vaultId)
        assertEquals(
            seeded.keyInformation.wrappedKey.toList(),
            after.keyInformation.wrappedKey.toList()
        )
    }

    @Test
    fun `missing source vault fails with VaultNotFound`() = runTest {
        seedVaults(dstVault)
        val missingId = newVaultId()

        val result = useCase(missingId, dstVault.id)

        assertTrue(result.isFailure())
        val error = assertIs<MoveItemsError.VaultNotFound>(result.error)
        assertEquals(missingId, error.vaultId)
    }

    @Test
    fun `missing destination vault fails with VaultNotFound`() = runTest {
        seedVaults(srcVault)
        val missingId = newVaultId()

        val result = useCase(srcVault.id, missingId)

        assertTrue(result.isFailure())
        val error = assertIs<MoveItemsError.VaultNotFound>(result.error)
        assertEquals(missingId, error.vaultId)
    }

    @Test
    fun `empty source vault is a success`() = runTest {
        seedVaults(srcVault, dstVault)

        val result = useCase(srcVault.id, dstVault.id)

        assertTrue(result.isSuccess())
    }

    @Test
    fun `multiple items all migrate to the destination vault`() = runTest {
        seedVaults(srcVault, dstVault)
        val seededIds = (1..3).map {
            encryptAndSeed(srcVault, name = "item-$it", passwordPlaintext = "pw-$it").id
        }

        val result = useCase(srcVault.id, dstVault.id)

        assertTrue(result.isSuccess())
        seededIds.forEach { id ->
            val moved = passwordRepository.getPasswordById(id)!!
            assertEquals(dstVault.id, moved.vaultId)
        }
    }

    @Test
    fun `persist failure rolls back all items and surfaces PersistFailed`() = runTest {
        seedVaults(srcVault, dstVault)
        val seededIds = (1..3).map {
            encryptAndSeed(srcVault, name = "item-$it", passwordPlaintext = "pw-$it").id
        }
        val cause = RuntimeException("write blew up")
        itemRepository.failMoveForId = seededIds[1] to cause

        val result = useCase(srcVault.id, dstVault.id)

        assertTrue(result.isFailure())
        val error = assertIs<MoveItemsError.PersistFailed>(result.error)
        assertSame(cause, error.cause)

        seededIds.forEach { id ->
            assertEquals(srcVault.id, passwordRepository.getPasswordById(id)!!.vaultId)
        }
    }

    @Test
    fun `rewrap failure surfaces ItemMoveFailed and leaves all items in src`() = runTest {
        seedVaults(srcVault, dstVault)
        val seededIds = (1..3).map {
            encryptAndSeed(srcVault, name = "item-$it", passwordPlaintext = "pw-$it").id
        }
        val failingId = seededIds[1]
        val cause = KeyWrapException.UnwrapFailed()
        keyWrapper.failUnwrapItemForId = failingId to cause

        val result = useCase(srcVault.id, dstVault.id)

        assertTrue(result.isFailure())
        val error = assertIs<MoveItemsError.ItemMoveFailed>(result.error)
        assertEquals(failingId, error.itemId)
        assertSame(cause, error.cause)

        seededIds.forEach { id ->
            assertEquals(srcVault.id, passwordRepository.getPasswordById(id)!!.vaultId)
        }
    }

    @Test
    fun `onProgress emits zero then increments up to total`() = runTest {
        seedVaults(srcVault, dstVault)
        val total = 3
        repeat(total) { i ->
            encryptAndSeed(srcVault, name = "item-$i", passwordPlaintext = "pw-$i")
        }
        val captured = mutableListOf<MoveItemsProgress>()

        val result = useCase(srcVault.id, dstVault.id) { captured += it }

        assertTrue(result.isSuccess())
        assertEquals(
            expected = listOf(
                MoveItemsProgress(movedCount = 0, total = total),
                MoveItemsProgress(movedCount = 1, total = total),
                MoveItemsProgress(movedCount = 2, total = total),
                MoveItemsProgress(movedCount = 3, total = total),
            ),
            actual = captured,
        )
    }

    @Test
    fun `onProgress emits a single zero-of-zero for an empty vault`() = runTest {
        seedVaults(srcVault, dstVault)
        val captured = mutableListOf<MoveItemsProgress>()

        useCase(srcVault.id, dstVault.id) { captured += it }

        assertEquals(listOf(MoveItemsProgress(movedCount = 0, total = 0)), captured)
    }

    @Test
    fun `onProgress is not invoked for a same-vault no-op`() = runTest {
        seedVaults(srcVault)
        encryptAndSeed(srcVault, passwordPlaintext = "x")
        val captured = mutableListOf<MoveItemsProgress>()

        useCase(srcVault.id, srcVault.id) { captured += it }

        assertTrue(captured.isEmpty())
    }

    @Test
    fun `onProgress stops emitting once a rewrap fails`() = runTest {
        seedVaults(srcVault, dstVault)
        val seededIds = (1..3).map {
            encryptAndSeed(srcVault, name = "item-$it", passwordPlaintext = "pw-$it").id
        }
        keyWrapper.failUnwrapItemForId = seededIds[1] to KeyWrapException.UnwrapFailed()
        val captured = mutableListOf<MoveItemsProgress>()

        useCase(srcVault.id, dstVault.id) { captured += it }

        assertEquals(
            expected = listOf(
                MoveItemsProgress(movedCount = 0, total = 3),
                MoveItemsProgress(movedCount = 1, total = 3),
            ),
            actual = captured,
        )
    }

    // --- helpers ---

    private fun makeVault(name: String, id: VaultId = newVaultId()): Vault {
        val vaultKey = ByteArray(32) { (id.hashCode() + it).toByte() }
        val wrapped = keyWrapper.wrapVaultKey(
            ark = session.dek.key.encoded,
            vaultKey = vaultKey,
            vaultId = id,
        )
        return Vault(
            id = id,
            name = name,
            keyInformation = KeyInformation(
                wrappedKey = wrapped.ciphertext,
                keyNonce = wrapped.nonce,
            ),
            icon = Vault.Icon.Default,
        )
    }

    private suspend fun seedVaults(vararg vaults: Vault) {
        vaultRepository.seed(*vaults)
    }

    private suspend fun encryptAndSeed(
        vault: Vault,
        id: ItemId = newItemId(),
        name: String = "item",
        username: String? = null,
        note: String? = null,
        passwordScore: PasswordScore = PasswordScore.Strong,
        domainInfos: Set<DomainInfo> = emptySet(),
        pinned: Boolean = false,
        passwordPlaintext: String,
        totpPlaintext: String? = null,
    ): Login {
        val aad = ItemAad(itemId = id, vaultId = vault.id)
        val login = cryptographicScopeProvider.itemScope(
            wrappedVaultKeyInformation = WrappedVaultKeyInformation(
                wrappedVaultKey = vault.keyInformation,
                vaultId = vault.id,
            ),
            wrappedItemKeyInformation = WrappedItemKeyInformation(itemAad = aad),
        ) {
            Login(
                id = id,
                name = name,
                username = username,
                domainInfos = domainInfos,
                passwordScore = passwordScore,
                password = passwordPlaintext.encryptSecretData(label = Login.LABEL_PASSWORD),
                totp = totpPlaintext?.encryptSecretData(label = Login.LABEL_TOTP_SECRET)?.let {
                    Totp(
                        loginId = id,
                        secret = it,
                        accountName = "",
                    )
                },
                note = note,
                pinned = pinned,
                vaultId = vault.id,
                keyInformation = wrapCurrentItemKey(),
            )
        }
        passwordRepository.seed(login)
        return login
    }

    private suspend fun decrypt(login: Login, vault: Vault): Pair<String, String?> =
        cryptographicScopeProvider.itemScope(
            wrappedVaultKeyInformation = WrappedVaultKeyInformation(
                wrappedVaultKey = vault.keyInformation,
                vaultId = vault.id,
            ),
            wrappedItemKeyInformation = login.wrappedItemKeyInformation(),
        ) {
            val pw: String = login.password.decryptSecretData(label = Login.LABEL_PASSWORD)
            val totp: String? =
                login.totp?.secret?.decryptSecretData(label = Login.LABEL_TOTP_SECRET)
            pw to totp
        }
}
