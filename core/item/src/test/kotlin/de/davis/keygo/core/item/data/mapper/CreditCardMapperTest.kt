package de.davis.keygo.core.item.data.mapper

import de.davis.keygo.core.item.data.local.entity.CreditCardEntity
import de.davis.keygo.core.item.data.local.entity.ItemEntity
import de.davis.keygo.core.item.data.local.entity.TagEntity
import de.davis.keygo.core.item.data.local.pojo.CreditCardProjection
import de.davis.keygo.core.item.data.local.pojo.ItemProjection
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.CreditCard
import de.davis.keygo.core.item.domain.model.EncryptedPayload
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Tag
import de.davis.keygo.core.item.domain.model.Timestamp
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import de.davis.keygo.core.item.data.local.entity.KeyInformation as EntityKeyInformation
import de.davis.keygo.core.item.data.local.entity.Timestamp as EntityTimestamp

class CreditCardMapperTest {

    @Test
    fun `toCreditCardEntity preserves payloads and scalar fields`() {
        val cardNumber = EncryptedPayload(byteArrayOf(1), byteArrayOf(2))
        val cvv = EncryptedPayload(byteArrayOf(3), byteArrayOf(4))
        val card = baseCard(
            cardNumber = CreditCard.CardNumber(cardNumber),
            cvv = CreditCard.CVV(cvv),
        )

        val entity = card.toCreditCardEntity()

        assertEquals(card.id, entity.id)
        assertEquals("Alice", entity.holder)
        assertEquals(cardNumber, entity.cardNumber)
        assertEquals(cvv, entity.cvv)
        assertEquals(YearMonth.of(2030, 12), entity.expirationDate)
    }

    @Test
    fun `toCreditCardEntity preserves null holder`() {
        val entity = baseCard(holder = null).toCreditCardEntity()
        assertEquals(null, entity.holder)
    }

    @Test
    fun `toDomain maps all fields from projection`() {
        val id = newItemId()
        val vaultId = newVaultId()
        val cardNumber = EncryptedPayload(byteArrayOf(1), byteArrayOf(2))
        val cvv = EncryptedPayload(byteArrayOf(3), byteArrayOf(4))
        val projection = baseProjection(
            id = id,
            vaultId = vaultId,
            cardNumber = cardNumber,
            cvv = cvv,
        )

        val card = projection.toDomain()

        assertEquals(id, card.id)
        assertEquals(vaultId, card.vaultId)
        assertEquals("Test Card", card.name)
        assertEquals("note", card.note)
        assertEquals(true, card.pinned)
        assertEquals("Alice", card.holder)
        assertEquals(cardNumber, card.cardNumber?.payload)
        assertEquals(cvv, card.cvv?.payload)
        assertEquals(YearMonth.of(2030, 12), card.expirationDate)
        assertEquals(VaultItemType.CreditCard, card.itemType)
    }

    @Test
    fun `toCreditCardEntity with null cardNumber stores null payload`() {
        val entity = baseCard(cardNumber = null).toCreditCardEntity()
        assertEquals(null, entity.cardNumber)
    }

    @Test
    fun `toCreditCardEntity with null expirationDate stores null`() {
        val entity = baseCard(expirationDate = null).toCreditCardEntity()
        assertEquals(null, entity.expirationDate)
    }

    @Test
    fun `toDomain with null cardNumber in entity maps to null CardNumber`() {
        val projection = baseProjection(cardNumber = null)
        assertNull(projection.toDomain().cardNumber)
    }

    @Test
    fun `toDomain with null expirationDate in entity maps to null`() {
        val projection = baseProjection(expirationDate = null)
        assertNull(projection.toDomain().expirationDate)
    }

    @Test
    fun `toDomain maps tag entity values to domain tags`() {
        val projection = baseProjection(
            tags = setOf(
                TagEntity(id = 1, value = "Work", normalized = "work"),
                TagEntity(id = 2, value = "Finance", normalized = "finance"),
            ),
        )

        val card = projection.toDomain()

        assertEquals(setOf(Tag.of("Work")!!, Tag.of("Finance")!!), card.tags)
    }

    private fun baseCard(
        id: ItemId = newItemId(),
        holder: String? = "Alice",
        cardNumber: CreditCard.CardNumber? = CreditCard.CardNumber(EncryptedPayload.EMPTY),
        cvv: CreditCard.CVV? = CreditCard.CVV(EncryptedPayload.EMPTY),
        expirationDate: YearMonth? = YearMonth.of(2030, 12),
    ): CreditCard = CreditCard(
        id = id,
        vaultId = newVaultId(),
        name = "Test Card",
        keyInformation = KeyInformation(byteArrayOf(), byteArrayOf()),
        tags = emptySet(),
        note = null,
        pinned = false,
        holder = holder,
        cardNumber = cardNumber,
        cvv = cvv,
        expirationDate = expirationDate,
        timestamp = Timestamp(),
    )

    private fun baseProjection(
        id: ItemId = newItemId(),
        vaultId: de.davis.keygo.core.item.domain.alias.VaultId = newVaultId(),
        cardNumber: EncryptedPayload? = EncryptedPayload.EMPTY,
        cvv: EncryptedPayload = EncryptedPayload.EMPTY,
        expirationDate: YearMonth? = YearMonth.of(2030, 12),
        tags: Set<TagEntity> = emptySet(),
    ): CreditCardProjection = CreditCardProjection(
        creditCardEntity = CreditCardEntity(
            id = id,
            holder = "Alice",
            cardNumber = cardNumber,
            cvv = cvv,
            expirationDate = expirationDate,
        ),
        item = ItemProjection(
            itemEntity = ItemEntity(
                id = id,
                vaultId = vaultId,
                name = "Test Card",
                note = "note",
                itemType = VaultItemType.CreditCard,
                pinned = true,
                keyInformation = EntityKeyInformation(
                    wrappedKey = byteArrayOf(),
                    keyNonce = byteArrayOf(),
                ),
                timestamp = EntityTimestamp(createdAt = 0L, modifiedAt = null),
            ),
            tags = tags,
        ),
    )
}
