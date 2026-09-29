package de.davis.keygo.feature.list_screen.domain.usecase

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.CredentialType
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.item.domain.model.Tag
import de.davis.keygo.core.item.domain.model.lite.LiteItem
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.feature.list_screen.domain.model.FilterFacet
import de.davis.keygo.feature.list_screen.domain.model.ItemAttributes
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AvailableFacetValuesUseCaseTest {

    private val useCase = AvailableFacetValuesUseCase()

    private data class TestLiteItem(
        override val name: String,
        override val id: ItemId = UUID.nameUUIDFromBytes(name.toByteArray()),
        override val itemType: VaultItemType = VaultItemType.Login,
        override val pinned: Boolean = false,
    ) : LiteItem

    private fun tag(value: String): Tag = Tag.of(value)!!

    @Test
    fun `item types include every type present`() {
        val result = useCase(
            items = listOf(
                TestLiteItem("Login"),
                TestLiteItem("Card", itemType = VaultItemType.CreditCard),
            ),
            attributes = ItemAttributes.None,
        )

        assertEquals(
            setOf(VaultItemType.Login, VaultItemType.CreditCard),
            result[FilterFacet.ItemTypes],
        )
    }

    @Test
    fun `tags include only tags carried by a visible item`() {
        val a = TestLiteItem("A")
        val b = TestLiteItem("B")

        val result = useCase(
            items = listOf(a, b),
            attributes = ItemAttributes(
                tagsByItem = mapOf(
                    a.id to setOf(tag("Bank")),
                    b.id to setOf(tag("Work")),
                ),
            ),
        )

        assertEquals(setOf(tag("Bank"), tag("Work")), result[FilterFacet.Tags])
    }

    @Test
    fun `tags belonging only to items absent from the list are excluded`() {
        val a = TestLiteItem("A")
        val hidden = TestLiteItem("Hidden")

        val result = useCase(
            items = listOf(a),
            attributes = ItemAttributes(
                tagsByItem = mapOf(
                    a.id to setOf(tag("Bank")),
                    hidden.id to setOf(tag("Secret")),
                ),
            ),
        )

        assertEquals(setOf(tag("Bank")), result[FilterFacet.Tags])
    }

    @Test
    fun `no tags, scores, credentials, or pinned flag when nothing carries them`() {
        val result = useCase(listOf(TestLiteItem("A")), ItemAttributes.None)

        assertTrue(result[FilterFacet.Tags].isEmpty())
        assertTrue(result[FilterFacet.PasswordScores].isEmpty())
        assertTrue(result[FilterFacet.Credentials].isEmpty())
        assertTrue(result[FilterFacet.Pinned].isEmpty())
    }

    @Test
    fun `scores are drawn only from items typed as logins`() {
        val login = TestLiteItem("Login")
        val card = TestLiteItem("Card", itemType = VaultItemType.CreditCard)

        val result = useCase(
            items = listOf(login, card),
            attributes = ItemAttributes(
                passwordScoreByItem = mapOf(
                    login.id to PasswordScore.Strong,
                    // A score keyed to a non-login id should never happen in practice, but must not
                    // leak into the available set if it does.
                    card.id to PasswordScore.Weak,
                ),
            ),
        )

        assertEquals(setOf(PasswordScore.Strong), result[FilterFacet.PasswordScores])
    }

    @Test
    fun `pinned is offered when any item is pinned`() {
        val result = useCase(
            items = listOf(TestLiteItem("A", pinned = true), TestLiteItem("B")),
            attributes = ItemAttributes.None,
        )

        assertEquals(setOf(true), result[FilterFacet.Pinned])
    }

    @Test
    fun `credentials are the union of what the listed logins hold`() {
        val a = TestLiteItem("A")
        val b = TestLiteItem("B")
        val card = TestLiteItem("Card", itemType = VaultItemType.CreditCard)

        val result = useCase(
            items = listOf(a, b, card),
            attributes = ItemAttributes(
                credentialsByItem = mapOf(
                    a.id to setOf(CredentialType.Password),
                    b.id to setOf(CredentialType.Password, CredentialType.Totp),
                    card.id to setOf(CredentialType.Passkey),
                ),
            ),
        )

        assertEquals(
            setOf(CredentialType.Password, CredentialType.Totp),
            result[FilterFacet.Credentials],
        )
    }
}
