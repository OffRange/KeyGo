package de.davis.keygo.core.item

import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.CreditCard
import de.davis.keygo.core.item.domain.model.KeyInformation
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FakeCreditCardRepositoryTest {

    private fun card(vaultId: java.util.UUID, name: String) = CreditCard(
        id = newItemId(),
        vaultId = vaultId,
        name = name,
        keyInformation = KeyInformation(byteArrayOf(), byteArrayOf()),
        tags = emptySet(),
        note = null,
        pinned = false,
        holder = null,
        cardNumber = null,
        cvv = null,
        expirationDate = null,
    )

    @Test
    fun `getCreditCardsByVault returns only cards in that vault`() = runTest {
        val vaultA = newVaultId()
        val vaultB = newVaultId()
        val repo = FakeCreditCardRepository()
        repo.seed(card(vaultA, "A1"), card(vaultA, "A2"), card(vaultB, "B1"))

        val result = repo.getCreditCardsByVault(vaultA)

        assertEquals(setOf("A1", "A2"), result.map { it.name }.toSet())
    }
}
