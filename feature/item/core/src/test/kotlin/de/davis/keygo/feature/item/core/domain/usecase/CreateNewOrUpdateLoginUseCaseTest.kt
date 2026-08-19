package de.davis.keygo.feature.item.core.domain.usecase

import de.davis.keygo.core.item.FakeCreditCardRepository
import de.davis.keygo.core.item.FakeItemRepository
import de.davis.keygo.core.item.FakeLoginRepository
import de.davis.keygo.core.item.FakePasswordStrengthEstimator
import de.davis.keygo.core.item.FakeVaultRepository
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.EncryptedPayload
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Login
import de.davis.keygo.core.item.domain.model.PasskeyRef
import de.davis.keygo.core.item.domain.model.PasswordCredential
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.item.domain.model.PasswordSecret
import de.davis.keygo.core.item.domain.model.Timestamp
import de.davis.keygo.core.item.domain.model.Totp
import de.davis.keygo.core.item.domain.model.Vault
import de.davis.keygo.core.item.domain.usecase.UpsertVaultItemUseCase
import de.davis.keygo.core.item.passkeyRef
import de.davis.keygo.core.security.crypto.FakeCryptographicScopeProvider
import de.davis.keygo.core.security.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.getOrNull
import de.davis.keygo.core.util.isFailure
import de.davis.keygo.core.util.isSuccess
import de.davis.keygo.feature.item.core.domain.model.ItemUpsertError
import de.davis.keygo.feature.item.core.domain.model.UpsertLogin
import de.davis.keygo.feature.item.core.domain.model.clear
import de.davis.keygo.feature.item.core.domain.model.set
import de.davis.keygo.rust.FakeTotpService
import de.davis.keygo.rust.totp.TotpService
import de.davisalessandro.keygo.rust.Algorithm
import de.davisalessandro.keygo.rust.SecretStrength
import de.davisalessandro.keygo.rust.TotpInfo
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.test.runTest

class CreateNewOrUpdateLoginUseCaseTest {

    private val defaultVault = Vault(
        id = newVaultId(),
        name = "Default vault",
        keyInformation = KeyInformation(byteArrayOf(), byteArrayOf()),
        icon = Vault.Icon.Default,
    )

    private val vaultRepository = FakeVaultRepository()
    private val loginRepository = FakeLoginRepository()
    private val cryptoProvider =
        FakeCryptographicScopeProvider(FakeItemRepository(loginRepository))
    private val useCase = makeUseCase(loginRepository, vaultRepository)

    @BeforeTest
    fun setupVault() = runTest {
        vaultRepository.seed(defaultVault)
    }


    // Validation - Create

    @Test
    fun `create with blank name returns BlankName error`() = runTest {
        val result = useCase(
            UpsertLogin.create(vaultId = defaultVault.id, name = "", password = "secret")
        )

        assertTrue(result.isFailure())
        assertEquals(ItemUpsertError.BlankName, result.error.single())
    }

    @Test
    fun `create with whitespace-only name returns BlankName error`() = runTest {
        val result = useCase(
            UpsertLogin.create(vaultId = defaultVault.id, name = "   ", password = "secret")
        )

        assertTrue(result.isFailure())
        assertEquals(ItemUpsertError.BlankName, result.error.single())
    }

    @Test
    fun `create with no password no totp no username returns Empty`() = runTest {
        val result = useCase(
            UpsertLogin.create(
                vaultId = defaultVault.id,
                name = "My site",
                password = null,
                username = null,
                totpUriOrSecret = null,
            )
        )

        assertTrue(result.isFailure())
        assertEquals(setOf(ItemUpsertError.Empty), result.error)
    }

    @Test
    fun `create with a pending passkey and nothing else returns Success`() = runTest {
        val result = useCase(
            UpsertLogin.create(
                vaultId = defaultVault.id,
                name = "My site",
                password = null,
                username = null,
                totpUriOrSecret = null,
                pendingPasskey = true,
            )
        )

        assertTrue(result.isSuccess())
    }

    @Test
    fun `update clearing the only credential while a passkey is pending returns Success`() =
        runTest {
            val existing = testLogin(username = null, totp = null)
            loginRepository.seed(existing)

            val result = useCase(
                UpsertLogin.update(
                    itemId = existing.id,
                    password = clear(),
                    pendingPasskey = true,
                )
            )

            assertTrue(result.isSuccess())
        }

    // The use case does not touch the passkey table: it resolves the login's effective credentials,
    // and LoginRepository drops the rows that fall outside that set as part of the same write.
    // These assert the resolved set; LoginRepositoryImplTest covers the deletion itself.

    @Test
    fun `update deleting a passkey drops it from the saved login`() = runTest {
        val removed = passkeyRef("example.com")
        val kept = passkeyRef("example.org")
        val existing = testLogin(passkeys = setOf(removed, kept))
        loginRepository.seed(existing)

        val result = useCase(
            UpsertLogin.update(
                itemId = existing.id,
                removedPasskeys = setOf(removed),
            )
        )

        assertTrue(result.isSuccess())
        assertEquals(setOf(kept), loginRepository.getLoginById(existing.id)?.passkeys)
    }

    @Test
    fun `update deleting one of two passkeys for the same RP keeps the other`() = runTest {
        // Two accounts on one site are two credentials sharing an rp. Removals key on the
        // credential id, so dropping one leaves the other in place; keying on the rp would take
        // both while the dialog only ever spoke about one.
        val removed = passkeyRef("example.com", discriminator = "first")
        val kept = passkeyRef("example.com", discriminator = "second")
        val existing = testLogin(passkeys = setOf(removed, kept))
        loginRepository.seed(existing)

        val result = useCase(
            UpsertLogin.update(
                itemId = existing.id,
                removedPasskeys = setOf(removed),
            )
        )

        assertTrue(result.isSuccess())
        assertEquals(setOf(kept), loginRepository.getLoginById(existing.id)?.passkeys)
    }

    @Test
    fun `update deleting the only passkey of an otherwise empty login returns Empty`() = runTest {
        val existing = testLogin(
            username = null,
            passwordCredential = null,
            passkeys = setOf(passkeyRef("example.com")),
        )
        loginRepository.seed(existing)

        val result = useCase(
            UpsertLogin.update(
                itemId = existing.id,
                removedPasskeys = setOf(passkeyRef("example.com")),
            )
        )

        assertTrue(result.isFailure())
        assertContains(result.error, ItemUpsertError.Empty)
    }

    @Test
    fun `a passkey attached after the form loaded survives an unrelated removal`() = runTest {
        // The editing screen reads a login's passkeys once, and passkeys can be attached to an
        // existing login from the passkey activity while that screen is open. Removals travel as a
        // delta for exactly this reason: the effective set is resolved against a fresh read here,
        // so a passkey the form never saw is not in the delta and stays. The table below holds
        // both; the form that produced this delta only ever saw "example.com".
        val attachedLater = passkeyRef("attached-later.example")
        val existing = testLogin(passkeys = setOf(passkeyRef("example.com"), attachedLater))
        loginRepository.seed(existing)

        val result = useCase(
            UpsertLogin.update(
                itemId = existing.id,
                removedPasskeys = setOf(passkeyRef("example.com")),
            )
        )

        assertTrue(result.isSuccess())
        assertEquals(setOf(attachedLater), loginRepository.getLoginById(existing.id)?.passkeys)
    }

    @Test
    fun `update rejected as Empty never reaches the repository`() = runTest {
        val existing = testLogin(
            username = null,
            passwordCredential = null,
            passkeys = setOf(passkeyRef("example.com")),
        )
        loginRepository.seed(existing)

        useCase(
            UpsertLogin.update(
                itemId = existing.id,
                removedPasskeys = setOf(passkeyRef("example.com")),
            )
        )

        // Validation runs before the write, so the stored login still holds the passkey and the
        // repository never gets the chance to delete its row.
        assertEquals(
            setOf(passkeyRef("example.com")),
            loginRepository.getLoginById(existing.id)?.passkeys,
        )
    }

    @Test
    fun `update deleting the only passkey while a password remains returns Success`() = runTest {
        val existing = testLogin(passkeys = setOf(passkeyRef("example.com")))
        loginRepository.seed(existing)

        val result = useCase(
            UpsertLogin.update(
                itemId = existing.id,
                removedPasskeys = setOf(passkeyRef("example.com")),
            )
        )

        assertTrue(result.isSuccess())
        assertTrue(loginRepository.getLoginById(existing.id)?.passkeys.orEmpty().isEmpty())
    }

    @Test
    fun `a save can register one passkey and remove another at the same time`() = runTest {
        // Not reachable from the UI today: the passkey activity only opens the editor on a new
        // login, which holds no passkeys to remove. A guard against the two ever being treated as
        // alternatives again if that flow gains an "edit existing" entry point.
        val existing = testLogin(passkeys = setOf(passkeyRef("old.example")))
        loginRepository.seed(existing)

        val result = useCase(
            UpsertLogin.update(
                itemId = existing.id,
                removedPasskeys = setOf(passkeyRef("old.example")),
                pendingPasskey = true,
            )
        )

        assertTrue(result.isSuccess())
        assertTrue(loginRepository.getLoginById(existing.id)?.passkeys.orEmpty().isEmpty())
    }

    @Test
    fun `create with password only returns Success`() = runTest {
        val result = useCase(
            UpsertLogin.create(
                vaultId = defaultVault.id,
                name = "My site",
                password = "s3cr3t",
            )
        )
        assertTrue(result.isSuccess())
    }

    @Test
    fun `create with username only returns Success`() = runTest {
        val result = useCase(
            UpsertLogin.create(
                vaultId = defaultVault.id,
                name = "My site",
                password = null,
                username = "alice",
            )
        )
        assertTrue(result.isSuccess())
    }

    @Test
    fun `create with totp only returns Success`() = runTest {
        val result = useCase(
            UpsertLogin.create(
                vaultId = defaultVault.id,
                name = "My site",
                password = null,
                totpUriOrSecret = "JBSWY3DPEHPK3PXP",
            )
        )
        assertTrue(result.isSuccess())
    }

    // Validation - Update

    @Test
    fun `update with Keep name and Keep password is valid`() = runTest {
        val existing = testLogin()
        loginRepository.seed(existing)

        val result = useCase(UpsertLogin.update(itemId = existing.id))

        assertTrue(result.isSuccess())
    }

    @Test
    fun `update with Clear name returns BlankName error`() = runTest {
        val existing = testLogin()
        loginRepository.seed(existing)

        val result = useCase(UpsertLogin.update(itemId = existing.id, name = clear()))

        assertTrue(result.isFailure())
        assertEquals(ItemUpsertError.BlankName, result.error.single())
    }

    @Test
    fun `update with blank Set name returns BlankName error`() = runTest {
        val existing = testLogin()
        loginRepository.seed(existing)

        val result = useCase(UpsertLogin.update(itemId = existing.id, name = set("")))

        assertTrue(result.isFailure())
        assertEquals(ItemUpsertError.BlankName, result.error.single())
    }

    @Test
    fun `update clearing password preserves totp and succeeds`() = runTest {
        val base = testLogin()
        val existing = base.copy(
            totp = Totp(
                loginId = base.id,
                secret = Totp.Secret(EncryptedPayload.EMPTY),
                accountName = "alice",
                issuer = "example",
            ),
        )
        loginRepository.seed(existing)

        val result = useCase(UpsertLogin.update(itemId = existing.id, password = clear()))

        assertTrue(result.isSuccess())
        val saved = loginRepository.getLoginById(existing.id)!!
        assertNull(saved.passwordCredential)
        assertNotNull(saved.totp)
    }

    @Test
    fun `update clearing the only credential returns Empty`() = runTest {
        val existing = testLogin(username = null, totp = null)
        loginRepository.seed(existing)

        val result = useCase(UpsertLogin.update(itemId = existing.id, password = clear()))

        assertTrue(result.isFailure())
        assertEquals(setOf(ItemUpsertError.Empty), result.error)
    }

    // Success - Create

    @Test
    fun `create with valid fields returns Success`() = runTest {
        val result = useCase(
            UpsertLogin.create(vaultId = defaultVault.id, name = "My site", password = "s3cr3t")
        )

        assertTrue(result.isSuccess())
    }

    @Test
    fun `create stores login with correct name`() = runTest {
        val result = useCase(
            UpsertLogin.create(vaultId = defaultVault.id, name = "My site", password = "s3cr3t")
        )

        val stored = storedById(result.getOrNull())
        assertNotNull(stored)
        assertEquals("My site", stored.name)
    }

    @Test
    fun `create with optional username and note stores them`() = runTest {
        val result = useCase(
            UpsertLogin.create(
                vaultId = defaultVault.id,
                name = "My site",
                password = "s3cr3t",
                username = "user@example.com",
                note = "A note",
            )
        )

        val stored = storedById(result.getOrNull())
        assertEquals("user@example.com", stored?.username)
        assertEquals("A note", stored?.note)
    }

    @Test
    fun `create without optional fields stores nulls`() = runTest {
        val result = useCase(
            UpsertLogin.create(vaultId = defaultVault.id, name = "My site", password = "s3cr3t")
        )

        val stored = storedById(result.getOrNull())
        assertEquals(null, stored?.username)
        assertEquals(null, stored?.note)
        assertEquals(null, stored?.totp)
    }

    @Test
    fun `create stores password strength from estimator`() = runTest {
        val freshLoginRepo = FakeLoginRepository()
        val freshVaultRepo = FakeVaultRepository()
        freshVaultRepo.seed(defaultVault)

        val localUseCase = makeUseCase(
            loginRepository = freshLoginRepo,
            vaultRepository = freshVaultRepo,
            estimator = FakePasswordStrengthEstimator(PasswordScore.Weak),
        )

        val result = localUseCase(
            UpsertLogin.create(vaultId = defaultVault.id, name = "My site", password = "123")
        )

        val stored = freshLoginRepo.getLoginById(result.getOrNull()!!)
        assertEquals(PasswordScore.Weak, stored?.passwordCredential!!.score)
    }

    @Test
    fun `update with new password re-evaluates password strength`() = runTest {
        val existing = testLogin(passwordScore = PasswordScore.Weak)
        loginRepository.seed(existing)

        // inject an estimator that returns Strong
        val localUseCase = makeUseCase(
            estimator = FakePasswordStrengthEstimator(PasswordScore.Strong)
        )

        localUseCase(UpsertLogin.update(itemId = existing.id, password = set("SuperS3cr3t!")))

        val updated = loginRepository.getLoginById(existing.id)
        assertEquals(PasswordScore.Strong, updated?.passwordCredential!!.score)
    }

    // Success - Update

    @Test
    fun `update with new name replaces name`() = runTest {
        val existing = testLogin(name = "Old name")
        loginRepository.seed(existing)

        useCase(UpsertLogin.update(itemId = existing.id, name = set("New name")))

        assertEquals("New name", loginRepository.getLoginById(existing.id)?.name)
    }

    @Test
    fun `update with Keep name preserves existing name`() = runTest {
        val existing = testLogin(name = "Preserved")
        loginRepository.seed(existing)

        useCase(UpsertLogin.update(itemId = existing.id))

        assertEquals("Preserved", loginRepository.getLoginById(existing.id)?.name)
    }

    @Test
    fun `update with new username replaces username`() = runTest {
        val existing = testLogin(username = "old@example.com")
        loginRepository.seed(existing)

        useCase(UpsertLogin.update(itemId = existing.id, username = set("new@example.com")))

        assertEquals("new@example.com", loginRepository.getLoginById(existing.id)?.username)
    }

    @Test
    fun `update with Clear username sets username to null`() = runTest {
        val existing = testLogin(username = "user@example.com")
        loginRepository.seed(existing)

        val result = useCase(UpsertLogin.update(itemId = existing.id, username = clear()))

        assertTrue(result.isSuccess(), "result: $result")
        assertEquals(null, loginRepository.getLoginById(existing.id)?.username)
    }

    @Test
    fun `update with Clear totpSecret removes totp`() = runTest {
        val base = testLogin()
        val existing = base.copy(
            totp = Totp(loginId = base.id, secret = Totp.Secret(EncryptedPayload.EMPTY)),
        )
        loginRepository.seed(existing)

        val result = useCase(UpsertLogin.update(itemId = existing.id, totpUriOrSecret = clear()))

        assertTrue(result.isSuccess(), "result: $result")
        assertEquals(null, loginRepository.getLoginById(existing.id)?.totp)
    }

    // Failure

    @Test
    fun `update with unknown id returns InvalidItemId error`() = runTest {
        val result = useCase(UpsertLogin.update(itemId = newItemId()))

        assertTrue(result.isFailure())
        assertContains(result.error, ItemUpsertError.InvalidItemId)
    }

    // Vault move on update

    @Test
    fun `update with different vaultId moves item to that vault`() = runTest {
        val otherVault = defaultVault.copy(id = newVaultId(), name = "Other vault")
        vaultRepository.seed(otherVault)

        val existing = testLogin()
        loginRepository.seed(existing)

        val result = useCase(
            UpsertLogin.update(itemId = existing.id, vaultId = otherVault.id)
        )

        assertTrue(result.isSuccess(), "result: $result")
        assertEquals(otherVault.id, loginRepository.getLoginById(existing.id)?.vaultId)
    }

    @Test
    fun `update with same vaultId keeps item in original vault`() = runTest {
        val existing = testLogin()
        loginRepository.seed(existing)

        useCase(UpsertLogin.update(itemId = existing.id, vaultId = defaultVault.id))

        assertEquals(defaultVault.id, loginRepository.getLoginById(existing.id)?.vaultId)
    }

    @Test
    fun `update with null vaultId keeps item in original vault`() = runTest {
        val existing = testLogin()
        loginRepository.seed(existing)

        useCase(UpsertLogin.update(itemId = existing.id, name = set("Renamed")))

        assertEquals(defaultVault.id, loginRepository.getLoginById(existing.id)?.vaultId)
    }

    @Test
    fun `update with unknown target vaultId returns InvalidVaultId error`() = runTest {
        val existing = testLogin()
        loginRepository.seed(existing)

        val result = useCase(
            UpsertLogin.update(itemId = existing.id, vaultId = newVaultId())
        )

        assertTrue(result.isFailure())
        assertContains(result.error, ItemUpsertError.InvalidVaultId)
    }

    @Test
    fun `update moving vaults also applies field changes`() = runTest {
        val otherVault = defaultVault.copy(id = newVaultId(), name = "Other vault")
        vaultRepository.seed(otherVault)

        val existing = testLogin(name = "Old name")
        loginRepository.seed(existing)

        useCase(
            UpsertLogin.update(
                itemId = existing.id,
                vaultId = otherVault.id,
                name = set("New name"),
            )
        )

        val stored = loginRepository.getLoginById(existing.id)
        assertEquals(otherVault.id, stored?.vaultId)
        assertEquals("New name", stored?.name)
    }

    @Test
    fun `update with different vaultId rewraps item key under the destination vault`() = runTest {
        val otherVault = Vault(
            id = newVaultId(),
            name = "Other vault",
            keyInformation = KeyInformation(
                wrappedKey = byteArrayOf(0x0A),
                keyNonce = byteArrayOf(0x0B),
            ),
            icon = Vault.Icon.Default,
        )
        vaultRepository.seed(otherVault)

        val existingItemKey = KeyInformation(
            wrappedKey = byteArrayOf(0x11, 0x22),
            keyNonce = byteArrayOf(0x33, 0x44),
        )
        val existing = testLogin().copy(keyInformation = existingItemKey)
        loginRepository.seed(existing)

        val rewrappedItemKey = KeyInformation(
            wrappedKey = byteArrayOf(0xAA.toByte(), 0xBB.toByte()),
            keyNonce = byteArrayOf(0xCC.toByte(), 0xDD.toByte()),
        )
        cryptoProvider.rewrapResult = Result.Success(rewrappedItemKey)

        val result = useCase(
            UpsertLogin.update(itemId = existing.id, vaultId = otherVault.id)
        )

        assertTrue(result.isSuccess(), "result: $result")

        val rewrap = cryptoProvider.rewrapCalls.single()
        assertEquals(defaultVault.id, rewrap.sourceVault.vaultId)
        assertContentEquals(
            defaultVault.keyInformation.wrappedKey,
            rewrap.sourceVault.wrappedVaultKey.wrappedKey,
        )
        assertEquals(otherVault.id, rewrap.destinationVault.vaultId)
        assertContentEquals(
            otherVault.keyInformation.wrappedKey,
            rewrap.destinationVault.wrappedVaultKey.wrappedKey,
        )
        assertEquals(existing.id, rewrap.sourceItem.itemAad.itemId)
        assertEquals(defaultVault.id, rewrap.sourceItem.itemAad.vaultId)
        assertContentEquals(
            existingItemKey.wrappedKey,
            rewrap.sourceItem.wrappedItemKey?.wrappedKey,
        )

        val stored = loginRepository.getLoginById(existing.id)
        assertNotNull(stored)
        assertEquals(otherVault.id, stored.vaultId)
        assertContentEquals(rewrappedItemKey.wrappedKey, stored.keyInformation.wrappedKey)
        assertContentEquals(rewrappedItemKey.keyNonce, stored.keyInformation.keyNonce)
    }

    @Test
    fun `update with same vaultId does not rewrap`() = runTest {
        val existing = testLogin()
        loginRepository.seed(existing)

        useCase(UpsertLogin.update(itemId = existing.id, vaultId = defaultVault.id))

        assertTrue(cryptoProvider.rewrapCalls.isEmpty())
    }

    // Crypto scope

    @Test
    fun `create routes secret fields through the crypto scope`() = runTest {
        val plaintextPassword = "s3cr3t"
        val plaintextTotp = "JBSWY3DPEHPK3PXP"

        val result = useCase(
            UpsertLogin.create(
                vaultId = defaultVault.id,
                name = "My site",
                password = plaintextPassword,
                totpUriOrSecret = plaintextTotp,
            )
        )

        val stored = storedById(result.getOrNull())
        assertNotNull(stored)

        val labels = cryptoProvider.encryptCalls.map { it.label }
        assertContains(labels, PasswordSecret.label)
        assertContains(labels, Totp.Secret.label)
        assertEquals(2, labels.size)

        val passwordCall = cryptoProvider.encryptCalls.single { it.label == PasswordSecret.label }
        assertContentEquals(plaintextPassword.encodeToByteArray(), passwordCall.plaintext)

        val totpCall = cryptoProvider.encryptCalls.single { it.label == Totp.Secret.label }
        assertContentEquals(plaintextTotp.encodeToByteArray(), totpCall.plaintext)

        val storedSecret = stored.passwordCredential!!.secret
        assertFalse(storedSecret.payload.ciphertext.contentEquals(plaintextPassword.encodeToByteArray()))
        assertContentEquals(
            FakeCryptographicScopeProvider.transform(plaintextPassword.encodeToByteArray()),
            storedSecret.payload.ciphertext
        )
        assertContentEquals(FakeCryptographicScopeProvider.IV, storedSecret.payload.iv)

        assertNotNull(stored.totp)
        assertFalse(stored.totp!!.secret.payload.ciphertext.contentEquals(plaintextTotp.encodeToByteArray()))
        assertContentEquals(
            FakeCryptographicScopeProvider.transform(plaintextTotp.encodeToByteArray()),
            stored.totp!!.secret.payload.ciphertext
        )
        assertContentEquals(FakeCryptographicScopeProvider.IV, stored.totp!!.secret.payload.iv)
    }

    @Test
    fun `repository failure on create wraps error in DatabaseError`() = runTest {
        val cause = RuntimeException("disk full")
        loginRepository.createOrUpdateError = cause

        val result = useCase(
            UpsertLogin.create(vaultId = defaultVault.id, name = "My site", password = "s3cr3t")
        )

        assertTrue(result.isFailure())
        val error = assertIs<ItemUpsertError.DatabaseError>(result.error.single())
        assertEquals(cause, error.throwable)
    }

    // TOTP handling - Create

    @Test
    fun `create with plain secret stores totp with only the secret and default fields`() = runTest {
        val result = useCase(
            UpsertLogin.create(
                vaultId = defaultVault.id,
                name = "My site",
                password = "s3cr3t",
                totpUriOrSecret = "JBSWY3DPEHPK3PXP",
            )
        )

        val totp = storedById(result.getOrNull())?.totp
        assertNotNull(totp)
        assertEquals(Totp.DEFAULT_ALGORITHM, totp.algorithm)
        assertEquals(Totp.DEFAULT_DIGITS, totp.digits)
        assertEquals(Totp.DEFAULT_PERIOD, totp.period)
        assertNull(totp.issuer)
        assertNull(totp.accountName)
    }

    @Test
    fun `create with valid totp uri stores all parsed metadata`() = runTest {
        val parsed = TotpInfo(
            secret = "JBSWY3DPEHPK3PXP",
            issuer = "GitHub",
            accountName = "alice@github.com",
            algorithm = Algorithm.SHA256,
            digits = 8,
            period = 60,
            strength = SecretStrength.TRUSTWORTHY,
        )
        val localUseCase = makeUseCase(
            totpService = FakeTotpService().apply { infoFromUriResult = parsed },
        )

        val result = localUseCase(
            UpsertLogin.create(
                vaultId = defaultVault.id,
                name = "My site",
                password = "s3cr3t",
                totpUriOrSecret = "otpauth://totp/GitHub:alice@github.com?secret=JBSWY3DPEHPK3PXP",
            )
        )

        val totp = storedById(result.getOrNull())?.totp
        assertNotNull(totp)
        assertEquals("sha256", totp.algorithm)
        assertEquals(8, totp.digits)
        assertEquals(60, totp.period)
        assertEquals("GitHub", totp.issuer)
        assertEquals("alice@github.com", totp.accountName)
    }

    @Test
    fun `create with blank totp field stores no totp`() = runTest {
        val result = useCase(
            UpsertLogin.create(
                vaultId = defaultVault.id,
                name = "My site",
                password = "s3cr3t",
                totpUriOrSecret = "   ",
            )
        )

        assertNull(storedById(result.getOrNull())?.totp)
    }

    @Test
    fun `create with null totp field stores no totp`() = runTest {
        val result = useCase(
            UpsertLogin.create(
                vaultId = defaultVault.id,
                name = "My site",
                password = "s3cr3t",
                totpUriOrSecret = null,
            )
        )

        assertNull(storedById(result.getOrNull())?.totp)
    }

    // TOTP handling - Update

    @Test
    fun `update replacing totp uri with plain secret resets algorithm digits period issuer and account name`() =
        runTest {
            val base = testLogin()
            val existing = base.copy(
                totp = Totp(
                    loginId = base.id,
                    secret = Totp.Secret(EncryptedPayload.EMPTY),
                    algorithm = "sha256",
                    digits = 8,
                    period = 60,
                    issuer = "GitHub",
                    accountName = "alice@github.com",
                ),
            )
            loginRepository.seed(existing)

            // useCase uses FakeTotpService with no infoFromUriResult, so input is treated as plain secret
            val result = useCase(
                UpsertLogin.update(
                    itemId = existing.id,
                    totpUriOrSecret = set("NEWSECRET"),
                )
            )

            assertTrue(result.isSuccess(), "result: $result")
            val totp = loginRepository.getLoginById(existing.id)?.totp
            assertNotNull(totp)
            assertEquals(Totp.DEFAULT_ALGORITHM, totp.algorithm)
            assertEquals(Totp.DEFAULT_DIGITS, totp.digits)
            assertEquals(Totp.DEFAULT_PERIOD, totp.period)
            assertNull(totp.issuer)
            assertNull(totp.accountName)
        }

    @Test
    fun `update replacing plain secret with valid uri sets all parsed metadata`() = runTest {
        val base = testLogin()
        val existing = base.copy(
            totp = Totp(
                loginId = base.id,
                secret = Totp.Secret(EncryptedPayload.EMPTY),
            ),
        )
        loginRepository.seed(existing)

        val parsed = TotpInfo(
            secret = "JBSWY3DPEHPK3PXP",
            issuer = "Acme",
            accountName = "bob@acme.com",
            algorithm = Algorithm.SHA512,
            digits = 8,
            period = 60,
            strength = SecretStrength.TRUSTWORTHY,
        )
        val result = makeUseCase(
            totpService = FakeTotpService().apply { infoFromUriResult = parsed },
        )(
            UpsertLogin.update(
                itemId = existing.id,
                totpUriOrSecret = set("otpauth://totp/Acme:bob@acme.com?secret=JBSWY3DPEHPK3PXP"),
            )
        )

        assertTrue(result.isSuccess(), "result: $result")
        val totp = loginRepository.getLoginById(existing.id)?.totp
        assertNotNull(totp)
        assertEquals("sha512", totp.algorithm)
        assertEquals(8, totp.digits)
        assertEquals(60, totp.period)
        assertEquals("Acme", totp.issuer)
        assertEquals("bob@acme.com", totp.accountName)
    }

    @Test
    fun `update with keep on totp preserves existing metadata unchanged`() = runTest {
        val base = testLogin()
        val existing = base.copy(
            totp = Totp(
                loginId = base.id,
                secret = Totp.Secret(EncryptedPayload.EMPTY),
                algorithm = "sha256",
                digits = 8,
                period = 60,
                issuer = "GitHub",
                accountName = "alice@github.com",
            ),
        )
        loginRepository.seed(existing)

        val result = useCase(UpsertLogin.update(itemId = existing.id))

        assertTrue(result.isSuccess(), "result: $result")
        val totp = loginRepository.getLoginById(existing.id)?.totp
        assertNotNull(totp)
        assertEquals("sha256", totp.algorithm)
        assertEquals(8, totp.digits)
        assertEquals(60, totp.period)
        assertEquals("GitHub", totp.issuer)
        assertEquals("alice@github.com", totp.accountName)
    }

    // Timestamps

    @Test
    fun `create sets createdAt to now and leaves modifiedAt null`() = runTest {
        val before = Clock.System.now()

        val result = useCase(
            UpsertLogin.create(vaultId = defaultVault.id, name = "My site", password = "s3cr3t")
        )

        val after = Clock.System.now()
        val stored = storedById(result.getOrNull())
        assertNotNull(stored)
        assertTrue(stored.timestamp.createdAt >= before && stored.timestamp.createdAt <= after)
        assertNull(stored.timestamp.modifiedAt)
    }

    @Test
    fun `update sets modifiedAt to now and preserves createdAt`() = runTest {
        val existing = testLogin()
        loginRepository.seed(existing)

        val before = Clock.System.now()
        useCase(UpsertLogin.update(itemId = existing.id, name = set("New name")))
        val after = Clock.System.now()

        val updated = loginRepository.getLoginById(existing.id)
        assertNotNull(updated)
        assertEquals(existing.timestamp.createdAt, updated.timestamp.createdAt)
        val modifiedAt = updated.timestamp.modifiedAt
        assertNotNull(modifiedAt)
        assertTrue(modifiedAt >= before && modifiedAt <= after)
    }

    // Helpers

    private fun makeUseCase(
        loginRepository: FakeLoginRepository = this@CreateNewOrUpdateLoginUseCaseTest.loginRepository,
        vaultRepository: FakeVaultRepository = this@CreateNewOrUpdateLoginUseCaseTest.vaultRepository,
        estimator: FakePasswordStrengthEstimator = FakePasswordStrengthEstimator(),
        cryptographicScopeProvider: CryptographicScopeProvider = cryptoProvider,
        totpService: TotpService = FakeTotpService(),
    ) = CreateNewOrUpdateLoginUseCase(
        cryptographicScopeProvider = cryptographicScopeProvider,
        loginRepository = loginRepository,
        vaultRepository = vaultRepository,
        upsertVaultItem = UpsertVaultItemUseCase(loginRepository, FakeCreditCardRepository()),
        passwordStrengthEstimator = estimator,
        totpService = totpService,
    )

    private fun testLogin(
        name: String = "Test",
        username: String? = null,
        passwordScore: PasswordScore = PasswordScore.Strong,
        passwordCredential: PasswordCredential? = PasswordCredential(
            secret = PasswordSecret(EncryptedPayload.EMPTY),
            score = passwordScore,
        ),
        totp: Totp? = null,
        timestamp: Timestamp = Timestamp(),
        passkeys: Set<PasskeyRef> = emptySet(),
    ) = Login(
        id = newItemId(),
        name = name,
        username = username,
        domainInfos = emptySet(),
        passwordCredential = passwordCredential,
        totp = totp,
        passkeys = passkeys,
        note = null,
        pinned = false,
        vaultId = defaultVault.id,
        keyInformation = KeyInformation(byteArrayOf(), byteArrayOf()),
        timestamp = timestamp,
    )

    private suspend fun storedById(id: ItemId?): Login? {
        id ?: return null
        return loginRepository.getLoginById(id)
    }
}
