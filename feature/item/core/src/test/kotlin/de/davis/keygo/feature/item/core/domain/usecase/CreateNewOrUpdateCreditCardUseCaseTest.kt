package de.davis.keygo.feature.item.core.domain.usecase

import de.davis.keygo.core.item.FakeCreditCardRepository
import de.davis.keygo.core.item.FakeItemRepository
import de.davis.keygo.core.item.FakeLoginRepository
import de.davis.keygo.core.item.FakeVaultRepository
import de.davis.keygo.rust.FakeCardFormatter
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.CreditCard
import de.davis.keygo.core.item.domain.model.EncryptedPayload
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Vault
import de.davis.keygo.core.item.domain.usecase.UpsertVaultItemUseCase
import de.davis.keygo.core.security.crypto.FakeCryptographicScopeProvider
import de.davis.keygo.core.security.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.getOrNull
import de.davis.keygo.core.util.isFailure
import de.davis.keygo.core.util.isSuccess
import de.davis.keygo.feature.item.core.domain.model.CreditCardUpsertError
import de.davis.keygo.feature.item.core.domain.model.ItemUpsertError
import de.davis.keygo.feature.item.core.domain.model.UpsertCreditCard
import de.davis.keygo.feature.item.core.domain.model.clear
import de.davis.keygo.feature.item.core.domain.model.set
import kotlinx.coroutines.test.runTest
import java.time.YearMonth
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

class CreateNewOrUpdateCreditCardUseCaseTest {

    private val defaultVault = Vault(
        id = newVaultId(),
        name = "Default vault",
        keyInformation = KeyInformation(byteArrayOf(), byteArrayOf()),
        icon = Vault.Icon.Default,
    )

    private val vaultRepository = FakeVaultRepository()
    private val creditCardRepository = FakeCreditCardRepository()

    private val cryptoProvider =
        FakeCryptographicScopeProvider(FakeItemRepository(FakeLoginRepository()))
    private val cardFormatter = FakeCardFormatter()
    private val useCase = makeUseCase()

    @BeforeTest
    fun setupVault() = runTest {
        vaultRepository.seed(defaultVault)
    }

    @Test
    fun `create with blank name returns BlankName error`() = runTest {
        val result = useCase(
            UpsertCreditCard.create(
                vaultId = defaultVault.id,
                name = "",
                cardNumber = "4111111111111111",
                expirationDate = "05/30",
            )
        )

        assertTrue(result.isFailure())
        assertContains(result.error, ItemUpsertError.BlankName)
    }

    @Test
    fun `create with unparseable expiration returns InvalidExpiration error`() = runTest {
        val result = useCase(
            UpsertCreditCard.create(
                vaultId = defaultVault.id,
                name = "My card",
                cardNumber = "4111111111111111",
                expirationDate = "not-a-date",
            )
        )

        assertTrue(result.isFailure())
        assertContains(result.error, CreditCardUpsertError.InvalidExpiration)
    }

    @Test
    fun `create stores card with name parsed expiration and vault`() = runTest {
        val result = useCase(
            UpsertCreditCard.create(
                vaultId = defaultVault.id,
                name = "My card",
                cardNumber = "4111111111111111",
                expirationDate = "05/30",
                holder = "ALICE SMITH",
            )
        )

        val stored = storedById(result.getOrNull())
        assertNotNull(stored)
        assertEquals("My card", stored.name)
        assertEquals("ALICE SMITH", stored.holder)
        assertEquals(YearMonth.of(2030, 5), stored.expirationDate)
        assertEquals(defaultVault.id, stored.vaultId)
    }

    @Test
    fun `create without cvv stores null cvv`() = runTest {
        val result = useCase(
            UpsertCreditCard.create(
                vaultId = defaultVault.id,
                name = "My card",
                cardNumber = "4111111111111111",
                expirationDate = "05/30",
                cvv = null,
            )
        )

        assertNull(storedById(result.getOrNull())?.cvv)
    }

    @Test
    fun `create routes card number and cvv through the crypto scope`() = runTest {
        val plaintextNumber = "4111111111111111"
        val plaintextCvv = "123"

        val result = useCase(
            UpsertCreditCard.create(
                vaultId = defaultVault.id,
                name = "My card",
                cardNumber = plaintextNumber,
                expirationDate = "05/30",
                cvv = plaintextCvv,
            )
        )

        val stored = storedById(result.getOrNull())
        assertNotNull(stored)

        val labels = cryptoProvider.encryptCalls.map { it.label }
        assertContains(labels, CreditCard.CardNumber.label)
        assertContains(labels, CreditCard.CVV.label)

        val numberCall = cryptoProvider.encryptCalls.single { it.label == CreditCard.CardNumber.label }
        assertContentEquals(plaintextNumber.encodeToByteArray(), numberCall.plaintext)

        assertNotNull(stored.cardNumber)
        assertFalse(stored.cardNumber!!.payload.ciphertext.contentEquals(plaintextNumber.encodeToByteArray()))
        assertContentEquals(
            FakeCryptographicScopeProvider.transform(plaintextNumber.encodeToByteArray()),
            stored.cardNumber!!.payload.ciphertext,
        )
        assertContentEquals(FakeCryptographicScopeProvider.IV, stored.cardNumber!!.payload.iv)
    }

    @Test
    fun `repository failure on create wraps error in DatabaseError`() = runTest {
        creditCardRepository.createOrUpdateError = RuntimeException("disk full")

        val result = useCase(
            UpsertCreditCard.create(
                vaultId = defaultVault.id,
                name = "My card",
                cardNumber = "4111111111111111",
                expirationDate = "05/30",
            )
        )

        assertTrue(result.isFailure())
        val error = assertIs<ItemUpsertError.DatabaseError>(result.error.single())
        assertEquals("disk full", error.throwable.message)
    }

    @Test
    fun `update with unknown id returns InvalidItemId error`() = runTest {
        val result = useCase(UpsertCreditCard.update(itemId = newItemId()))

        assertTrue(result.isFailure())
        assertContains(result.error, ItemUpsertError.InvalidItemId)
    }

    @Test
    fun `update with new name replaces name and keeps other fields`() = runTest {
        val existing = testCard(name = "Old name")
        creditCardRepository.seed(existing)

        val result = useCase(UpsertCreditCard.update(itemId = existing.id, name = set("New name")))

        assertTrue(result.isSuccess(), "result: $result")
        val stored = creditCardRepository.getCreditCardById(existing.id)
        assertEquals("New name", stored?.name)
        assertEquals(existing.cardNumber, stored?.cardNumber)
    }

    @Test
    fun `update clearing cvv sets cvv to null`() = runTest {
        val existing = testCard(cvv = CreditCard.CVV(EncryptedPayload.EMPTY))
        creditCardRepository.seed(existing)

        val result = useCase(UpsertCreditCard.update(itemId = existing.id, cvv = clear()))

        assertTrue(result.isSuccess(), "result: $result")
        assertNull(creditCardRepository.getCreditCardById(existing.id)?.cvv)
    }

    @Test
    fun `update with Keep on cvv preserves existing cvv unchanged`() = runTest {
        val existingPayload = EncryptedPayload(
            ciphertext = byteArrayOf(0x01, 0x02, 0x03),
            iv = byteArrayOf(0x04, 0x05, 0x06),
        )
        val existing = testCard(cvv = CreditCard.CVV(existingPayload))
        creditCardRepository.seed(existing)

        val result = useCase(UpsertCreditCard.update(itemId = existing.id, name = set("x")))

        assertTrue(result.isSuccess(), "result: $result")
        val stored = creditCardRepository.getCreditCardById(existing.id)
        assertNotNull(stored)
        val storedCvv = stored.cvv
        assertNotNull(storedCvv)
        assertContentEquals(existingPayload.ciphertext, storedCvv.payload.ciphertext)
        assertContentEquals(existingPayload.iv, storedCvv.payload.iv)
    }

    @Test
    fun `update with new card number routes the new number through the crypto scope`() = runTest {
        val existing = testCard()
        creditCardRepository.seed(existing)

        val plaintextNumber = "5555444433332222"
        val result = useCase(UpsertCreditCard.update(itemId = existing.id, cardNumber = set(plaintextNumber)))

        assertTrue(result.isSuccess(), "result: $result")

        val numberCall = cryptoProvider.encryptCalls.single { it.label == CreditCard.CardNumber.label }
        assertContentEquals(plaintextNumber.encodeToByteArray(), numberCall.plaintext)

        val stored = creditCardRepository.getCreditCardById(existing.id)
        assertNotNull(stored)
        assertNotNull(stored.cardNumber)
        assertContentEquals(
            FakeCryptographicScopeProvider.transform(plaintextNumber.encodeToByteArray()),
            stored.cardNumber!!.payload.ciphertext,
        )
        assertContentEquals(FakeCryptographicScopeProvider.IV, stored.cardNumber!!.payload.iv)
    }

    @Test
    fun `update with blank Set name returns BlankName error`() = runTest {
        val existing = testCard()
        creditCardRepository.seed(existing)

        val result = useCase(UpsertCreditCard.update(itemId = existing.id, name = set("")))

        assertTrue(result.isFailure())
        assertContains(result.error, ItemUpsertError.BlankName)
    }

    @Test
    fun `create with invalid month expiration returns InvalidExpiration error`() = runTest {
        val result = useCase(
            UpsertCreditCard.create(
                vaultId = defaultVault.id,
                name = "My card",
                cardNumber = "4111111111111111",
                expirationDate = "13/30",
            )
        )

        assertTrue(result.isFailure())
        assertContains(result.error, CreditCardUpsertError.InvalidExpiration)
    }

    @Test
    fun `create without card number stores null cardNumber`() = runTest {
        val result = useCase(
            UpsertCreditCard.create(
                vaultId = defaultVault.id,
                name = "My card",
                holder = "Alice",
            )
        )

        val stored = storedById(result.getOrNull())
        assertNotNull(stored)
        assertNull(stored.cardNumber)
    }

    @Test
    fun `create with invalid card number returns InvalidCardNumber error`() = runTest {
        val result = makeUseCase(cardFormatter = FakeCardFormatter().also { it.validResult = { false } })(
            UpsertCreditCard.create(
                vaultId = defaultVault.id,
                name = "My card",
                cardNumber = "1234567890123456",
            )
        )

        assertTrue(result.isFailure())
        assertContains(result.error, CreditCardUpsertError.InvalidCardNumber)
    }

    @Test
    fun `create with cvv length not matching the network returns InvalidCvv error`() = runTest {
        // Card number sets the network; the fake reports an expected CVV length of 4, so a
        // 3-digit CVV is rejected.
        val formatter = FakeCardFormatter().also { it.cvvLenResult = { 4 } }
        val result = makeUseCase(cardFormatter = formatter)(
            UpsertCreditCard.create(
                vaultId = defaultVault.id,
                name = "My card",
                cardNumber = "378282246310005",
                cvv = "123",
            )
        )

        assertTrue(result.isFailure())
        assertContains(result.error, CreditCardUpsertError.InvalidCvv)
    }

    @Test
    fun `create with cvv length matching the network succeeds`() = runTest {
        val formatter = FakeCardFormatter().also { it.cvvLenResult = { 4 } }
        val result = makeUseCase(cardFormatter = formatter)(
            UpsertCreditCard.create(
                vaultId = defaultVault.id,
                name = "My card",
                cardNumber = "378282246310005",
                cvv = "1234",
            )
        )

        assertTrue(result.isSuccess(), "result: $result")
    }

    @Test
    fun `update of cvv alone is not length-checked when the card number is kept`() = runTest {
        // No card number in the upsert means the network is unknown here, so CVV length is not
        // enforced even though the fake would report a different expected length.
        val existing = testCard()
        creditCardRepository.seed(existing)
        val formatter = FakeCardFormatter().also { it.cvvLenResult = { 4 } }

        val result = makeUseCase(cardFormatter = formatter)(
            UpsertCreditCard.update(itemId = existing.id, cvv = set("123")),
        )

        assertTrue(result.isSuccess(), "result: $result")
    }

    @Test
    fun `update clearing card number sets cardNumber to null`() = runTest {
        val existing = testCard()
        creditCardRepository.seed(existing)

        val result = useCase(UpsertCreditCard.update(itemId = existing.id, cardNumber = clear()))

        assertTrue(result.isSuccess(), "result: $result")
        val stored = creditCardRepository.getCreditCardById(existing.id)
        assertNull(stored?.cardNumber)
    }

    @Test
    fun `update clearing expiration date sets expirationDate to null`() = runTest {
        val existing = testCard()
        creditCardRepository.seed(existing)

        val result = useCase(UpsertCreditCard.update(itemId = existing.id, expirationDate = clear()))

        assertTrue(result.isSuccess(), "result: $result")
        assertNull(creditCardRepository.getCreditCardById(existing.id)?.expirationDate)
    }

    @Test
    fun `update with same vaultId does not rewrap the item key`() = runTest {
        val existing = testCard()
        creditCardRepository.seed(existing)

        useCase(UpsertCreditCard.update(itemId = existing.id, vaultId = defaultVault.id, name = set("y")))

        assertTrue(cryptoProvider.rewrapCalls.isEmpty())
    }

    @Test
    fun `update with different vaultId rewraps item key under the destination vault`() = runTest {
        val otherVault = Vault(
            id = newVaultId(),
            name = "Other vault",
            keyInformation = KeyInformation(wrappedKey = byteArrayOf(0x0A), keyNonce = byteArrayOf(0x0B)),
            icon = Vault.Icon.Default,
        )
        vaultRepository.seed(otherVault)

        val existing = testCard().copy(
            keyInformation = KeyInformation(wrappedKey = byteArrayOf(0x11), keyNonce = byteArrayOf(0x33)),
        )
        creditCardRepository.seed(existing)

        val rewrapped = KeyInformation(wrappedKey = byteArrayOf(0xAA.toByte()), keyNonce = byteArrayOf(0xCC.toByte()))
        cryptoProvider.rewrapResult = Result.Success(rewrapped)

        val result = useCase(UpsertCreditCard.update(itemId = existing.id, vaultId = otherVault.id))

        assertTrue(result.isSuccess(), "result: $result")
        val stored = creditCardRepository.getCreditCardById(existing.id)
        assertNotNull(stored)
        assertEquals(otherVault.id, stored.vaultId)
        assertContentEquals(rewrapped.wrappedKey, stored.keyInformation.wrappedKey)
    }

    private fun makeUseCase(
        cryptographicScopeProvider: CryptographicScopeProvider = cryptoProvider,
        cardFormatter: FakeCardFormatter = this.cardFormatter,
    ) = CreateNewOrUpdateCreditCardUseCase(
        cryptographicScopeProvider = cryptographicScopeProvider,
        creditCardRepository = creditCardRepository,
        cardFormatter = cardFormatter,
        vaultRepository = vaultRepository,
        upsertVaultItem = UpsertVaultItemUseCase(FakeLoginRepository(), creditCardRepository),
    )

    private fun testCard(
        name: String = "Test card",
        cvv: CreditCard.CVV? = null,
    ) = CreditCard(
        id = newItemId(),
        vaultId = defaultVault.id,
        name = name,
        keyInformation = KeyInformation(byteArrayOf(), byteArrayOf()),
        tags = emptySet(),
        note = null,
        pinned = false,
        holder = "Test Holder",
        cardNumber = CreditCard.CardNumber(EncryptedPayload.EMPTY),
        cvv = cvv,
        expirationDate = YearMonth.of(2030, 5),
    )

    private suspend fun storedById(id: ItemId?): CreditCard? {
        id ?: return null
        return creditCardRepository.getCreditCardById(id)
    }
}
