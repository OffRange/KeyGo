package de.davis.keygo.core.item.domain.model

import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class CreditCardTest {

    @Test
    fun `itemType is CreditCard`() {
        assertEquals(VaultItemType.CreditCard, baseCard().itemType)
    }

    @Test
    fun `CardNumber equality uses content comparison for payload`() {
        val a = CreditCard.CardNumber(EncryptedPayload(byteArrayOf(1, 2), byteArrayOf(3)))
        val b = CreditCard.CardNumber(EncryptedPayload(byteArrayOf(1, 2), byteArrayOf(3)))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `CVV inequality when payload differs`() {
        val a = CreditCard.CVV(EncryptedPayload(byteArrayOf(1), byteArrayOf(2)))
        val b = CreditCard.CVV(EncryptedPayload(byteArrayOf(9), byteArrayOf(2)))
        assertNotEquals(a, b)
    }

    @Test
    fun `CardNumber blueprint round-trips a string through its codec`() {
        val codec = CreditCard.CardNumber.codec
        val original = "4242424242424242"
        assertEquals(original, codec.decode(codec.encode(original)))
    }

    @Test
    fun `CardNumber blueprint createField wraps the given payload`() {
        val payload = EncryptedPayload(byteArrayOf(7), byteArrayOf(8))
        assertEquals(payload, CreditCard.CardNumber.createField(payload).payload)
    }

    @Test
    fun `CVV blueprint createField wraps the given payload`() {
        val payload = EncryptedPayload(byteArrayOf(7), byteArrayOf(8))
        assertEquals(payload, CreditCard.CVV.createField(payload).payload)
    }

    private fun baseCard(): CreditCard = CreditCard(
        id = newItemId(),
        vaultId = newVaultId(),
        name = "Test Card",
        keyInformation = KeyInformation(byteArrayOf(), byteArrayOf()),
        tags = emptySet(),
        note = null,
        pinned = false,
        holder = "Alice",
        lastNumbers = "4242",
        cardNumber = CreditCard.CardNumber(EncryptedPayload.EMPTY),
        cvv = CreditCard.CVV(EncryptedPayload.EMPTY),
        expirationDate = YearMonth.of(2030, 12),
    )
}
