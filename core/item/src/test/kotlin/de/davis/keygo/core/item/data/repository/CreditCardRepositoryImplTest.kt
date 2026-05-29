package de.davis.keygo.core.item.data.repository

import androidx.room.withTransaction
import de.davis.keygo.core.item.data.local.dao.CreditCardDao
import de.davis.keygo.core.item.data.local.dao.ItemDao
import de.davis.keygo.core.item.data.local.datasource.ItemDatabase
import de.davis.keygo.core.item.data.local.entity.CreditCardEntity
import de.davis.keygo.core.item.data.local.entity.ItemEntity
import de.davis.keygo.core.item.data.local.pojo.CreditCardProjection
import de.davis.keygo.core.item.data.local.pojo.ItemProjection
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.CreditCard
import de.davis.keygo.core.item.domain.model.EncryptedPayload
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.core.util.isFailure
import de.davis.keygo.core.util.isSuccess
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import java.time.YearMonth
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import de.davis.keygo.core.item.data.local.entity.KeyInformation as EntityKeyInformation

class CreditCardRepositoryImplTest {

    private val database = mockk<ItemDatabase>()
    private val itemDao = mockk<ItemDao>(relaxed = true)
    private val creditCardDao = mockk<CreditCardDao>(relaxed = true)

    private val repository = CreditCardRepositoryImpl(
        database = database,
        itemDao = itemDao,
        creditCardDao = creditCardDao,
    )

    @BeforeTest
    fun setUp() {
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { database.withTransaction(any<suspend () -> Any?>()) } coAnswers {
            secondArg<suspend () -> Any?>().invoke()
        }
    }

    @AfterTest
    fun tearDown() {
        unmockkStatic("androidx.room.RoomDatabaseKt")
    }

    @Test
    fun `createOrUpdateCreditCard returns Success with card id`() = runTest {
        val card = testCreditCard()

        val result = repository.createOrUpdateCreditCard(card)

        assertTrue(result.isSuccess())
        assertEquals(card.id, result.success)
    }

    @Test
    fun `createOrUpdateCreditCard upserts both item and credit card rows`() = runTest {
        val card = testCreditCard()

        val result = repository.createOrUpdateCreditCard(card)

        assertTrue(result.isSuccess())
        coVerify(exactly = 1) { itemDao.upsert(any<ItemEntity>()) }
        coVerify(exactly = 1) { creditCardDao.upsert(any<CreditCardEntity>()) }
    }

    @Test
    fun `createOrUpdateCreditCard returns Failure when item upsert throws`() = runTest {
        val error = RuntimeException("db error")
        coEvery { itemDao.upsert(any<ItemEntity>()) } throws error

        val result = repository.createOrUpdateCreditCard(testCreditCard())

        assertTrue(result.isFailure())
        assertEquals(error, result.error)
    }

    @Test
    fun `createOrUpdateCreditCard returns Failure when credit card upsert throws`() = runTest {
        val error = RuntimeException("db error")
        coEvery { creditCardDao.upsert(any<CreditCardEntity>()) } throws error

        val result = repository.createOrUpdateCreditCard(testCreditCard())

        assertTrue(result.isFailure())
        assertEquals(error, result.error)
    }

    @Test
    fun `getCreditCardById maps projection to domain`() = runTest {
        val id = newItemId()
        coEvery { creditCardDao.getById(id) } returns projection(id)

        val card = repository.getCreditCardById(id)

        assertEquals(id, card?.id)
        assertEquals("Alice", card?.holder)
        assertEquals(YearMonth.of(2030, 12), card?.expirationDate)
    }

    @Test
    fun `getCreditCardById returns null when row is absent`() = runTest {
        val id = newItemId()
        coEvery { creditCardDao.getById(id) } returns null

        assertNull(repository.getCreditCardById(id))
    }

    @Test
    fun `observeCreditCardById emits mapped domain card`() = runTest {
        val id = newItemId()
        every { creditCardDao.observeById(id) } returns flowOf(projection(id))

        val card = repository.observeCreditCardById(id).first()

        assertEquals(id, card?.id)
        assertEquals("Alice", card?.holder)
    }

    private fun testCreditCard() = CreditCard(
        id = newItemId(),
        vaultId = newVaultId(),
        name = "Test Card",
        keyInformation = KeyInformation(byteArrayOf(), byteArrayOf()),
        tags = emptySet(),
        note = null,
        pinned = false,
        holder = "Alice",
        cardNumber = CreditCard.CardNumber(EncryptedPayload.EMPTY),
        cvv = CreditCard.CVV(EncryptedPayload.EMPTY),
        expirationDate = YearMonth.of(2030, 12),
    )

    private fun projection(id: ItemId) = CreditCardProjection(
        creditCardEntity = CreditCardEntity(
            id = id,
            holder = "Alice",
            cardNumber = EncryptedPayload.EMPTY,
            cvv = EncryptedPayload.EMPTY,
            expirationDate = YearMonth.of(2030, 12),
        ),
        item = ItemProjection(
            itemEntity = ItemEntity(
                id = id,
                vaultId = newVaultId(),
                name = "Test Card",
                note = null,
                itemType = VaultItemType.CreditCard,
                pinned = false,
                keyInformation = EntityKeyInformation(
                    wrappedKey = byteArrayOf(),
                    keyNonce = byteArrayOf(),
                ),
            ),
            tags = emptySet(),
        ),
    )
}
