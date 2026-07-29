package de.davis.keygo.migration.legacy_data.data.mapper

import de.davis.keygo.core.item.FakeItemRepository
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.CreditCard
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Login
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.security.crypto.FakeCryptographicScopeProvider
import de.davis.keygo.core.security.domain.crypto.decrypt
import de.davis.keygo.core.security.domain.crypto.model.WrappedItemKeyInformation
import de.davis.keygo.core.security.domain.crypto.model.WrappedVaultKeyInformation
import de.davis.keygo.core.util.assertSuccess
import de.davis.keygo.migration.legacy_data.data.FakeLegacyCipher
import de.davis.keygo.migration.legacy_data.data.FakeRegistrableDomainResolver
import de.davis.keygo.migration.legacy_data.data.crypto.LegacyCipher
import de.davis.keygo.migration.legacy_data.domain.model.LegacyDetail
import de.davis.keygo.migration.legacy_data.domain.model.LegacyItem
import de.davis.keygo.migration.legacy_data.domain.model.LegacyStrength
import de.davisalessandro.keygo.rust.ItemAad
import kotlinx.coroutines.test.runTest
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class LegacyItemConverterTest {

    private val vaultId = newVaultId()
    private val provider = FakeCryptographicScopeProvider(FakeItemRepository())

    private fun converter(cipher: LegacyCipher = FakeLegacyCipher()) = LegacyItemConverter(
        cipher = cipher,
        registrableDomainResolver = FakeRegistrableDomainResolver(),
    )

    private fun legacyItem(
        detail: LegacyDetail,
        title: String = "Example",
        favorite: Boolean = false,
        createdAt: Long? = 1_700_000_000_000L,
        modifiedAt: Long? = null,
        tags: Set<String> = emptySet(),
    ) = LegacyItem(
        legacyId = 7L,
        title = title,
        favorite = favorite,
        createdAt = createdAt,
        modifiedAt = modifiedAt,
        tags = tags,
        detail = detail,
    )

    private suspend fun convert(
        item: LegacyItem,
        cipher: LegacyCipher = FakeLegacyCipher(),
    ) = provider.itemScope(
        wrappedVaultKeyInformation = WrappedVaultKeyInformation(
            wrappedVaultKey = KeyInformation(byteArrayOf(), byteArrayOf()),
            vaultId = vaultId,
        ),
        wrappedItemKeyInformation = WrappedItemKeyInformation(
            itemAad = ItemAad(itemId = newItemId(), vaultId = vaultId),
        ),
    ) {
        // The itemScope receiver satisfies the converter's CryptographicScope context parameter.
        converter(cipher).convert(
            item = item,
            itemId = newItemId(),
            vaultId = vaultId,
            keyInformation = KeyInformation(byteArrayOf(1), byteArrayOf(2)),
        )
    }.assertSuccess()

    @Test
    fun `converts a password into a login with a decryptable secret`() = runTest {
        val converted = convert(
            legacyItem(
                LegacyDetail.Password(
                    username = "ada",
                    origin = "https://example.com",
                    password = "hunter2".encodeToByteArray(),
                    strength = LegacyStrength.STRONG,
                ),
            ),
        )

        val login = assertIs<Login>(converted)
        assertEquals("Example", login.name)
        assertEquals("ada", login.username)
        assertEquals(PasswordScore.Strong, login.passwordCredential?.score)

        val plaintext = provider.itemScope(
            wrappedVaultKeyInformation = WrappedVaultKeyInformation(
                wrappedVaultKey = KeyInformation(byteArrayOf(), byteArrayOf()),
                vaultId = vaultId,
            ),
            wrappedItemKeyInformation = WrappedItemKeyInformation(
                itemAad = ItemAad(itemId = login.id, vaultId = vaultId),
            ),
        ) {
            login.passwordCredential!!.secret.decrypt()
        }.assertSuccess()

        assertEquals("hunter2", plaintext)
    }

    @Test
    fun `maps every legacy strength onto its v2 score`() = runTest {
        val expected = mapOf(
            LegacyStrength.RIDICULOUS to PasswordScore.Ridiculous,
            LegacyStrength.WEAK to PasswordScore.Weak,
            LegacyStrength.MODERATE to PasswordScore.Moderate,
            LegacyStrength.STRONG to PasswordScore.Strong,
            LegacyStrength.VERY_STRONG to PasswordScore.Excellent,
        )

        expected.forEach { (legacy, score) ->
            val login = assertIs<Login>(
                convert(
                    legacyItem(
                        LegacyDetail.Password(
                            username = null,
                            origin = null,
                            password = "pw".encodeToByteArray(),
                            strength = legacy,
                        ),
                    ),
                ),
            )
            assertEquals(score, login.passwordCredential?.score)
        }
    }

    @Test
    fun `maps a missing strength onto None`() = runTest {
        val login = assertIs<Login>(
            convert(
                legacyItem(
                    LegacyDetail.Password(
                        username = null,
                        origin = null,
                        password = "pw".encodeToByteArray(),
                        strength = null,
                    ),
                ),
            ),
        )

        assertEquals(PasswordScore.None, login.passwordCredential?.score)
    }

    @Test
    fun `resolves the origin into a domain info`() = runTest {
        val login = assertIs<Login>(
            convert(
                legacyItem(
                    LegacyDetail.Password("ada", "https://example.com", null, null),
                ),
            ),
        )

        val domain = login.domainInfos.single()
        assertEquals("https://example.com", domain.value)
        assertEquals("example.com", domain.eTLD1)
    }

    @Test
    fun `yields no domain info for a blank origin`() = runTest {
        val login = assertIs<Login>(
            convert(legacyItem(LegacyDetail.Password("ada", "   ", null, null))),
        )

        assertTrue(login.domainInfos.isEmpty())
    }

    @Test
    fun `maps a blank username onto null`() = runTest {
        val login = assertIs<Login>(
            convert(legacyItem(LegacyDetail.Password("  ", null, null, null))),
        )

        assertNull(login.username)
    }

    @Test
    fun `yields no password credential when v1 stored none`() = runTest {
        val login = assertIs<Login>(
            convert(legacyItem(LegacyDetail.Password("ada", null, null, null))),
        )

        assertNull(login.passwordCredential)
    }

    @Test
    fun `returns null when the nested password blob does not decrypt`() = runTest {
        val blob = "hunter2".encodeToByteArray()

        val converted = convert(
            item = legacyItem(LegacyDetail.Password("ada", null, blob, null)),
            cipher = FakeLegacyCipher(failFor = blob),
        )

        assertNull(converted)
    }

    @Test
    fun `converts a credit card`() = runTest {
        val converted = convert(
            legacyItem(
                LegacyDetail.CreditCard(
                    firstName = "Ada",
                    lastName = "Lovelace",
                    cardNumber = "4111111111111111",
                    cvv = "123",
                    expirationDate = "04/29",
                ),
            ),
        )

        val card = assertIs<CreditCard>(converted)
        assertEquals("Ada Lovelace", card.holder)
        assertEquals(YearMonth.of(2029, 4), card.expirationDate)
    }

    @Test
    fun `maps a partial cardholder onto null, as v1 getFullName did`() = runTest {
        val card = assertIs<CreditCard>(
            convert(legacyItem(LegacyDetail.CreditCard("Ada", null, "4111", null, null))),
        )

        assertNull(card.holder)
    }

    @Test
    fun `parses a two digit year into the 2000s`() = runTest {
        val card = assertIs<CreditCard>(
            convert(legacyItem(LegacyDetail.CreditCard(null, null, null, null, "01/70"))),
        )

        assertEquals(YearMonth.of(2070, 1), card.expirationDate)
    }

    @Test
    fun `maps an unparseable expiration onto null`() = runTest {
        val card = assertIs<CreditCard>(
            convert(legacyItem(LegacyDetail.CreditCard(null, null, "4111", null, "nonsense"))),
        )

        assertNull(card.expirationDate)
    }

    @Test
    fun `maps a blank cvv onto null`() = runTest {
        val card = assertIs<CreditCard>(
            convert(legacyItem(LegacyDetail.CreditCard(null, null, "4111", "", null))),
        )

        assertNull(card.cvv)
    }

    @Test
    fun `carries favorite, tags and timestamps across`() = runTest {
        val converted = convert(
            legacyItem(
                detail = LegacyDetail.Password("ada", null, null, null),
                favorite = true,
                createdAt = 1_700_000_000_000L,
                modifiedAt = 1_700_000_999_000L,
                tags = setOf("work", "personal"),
            ),
        )!!

        assertTrue(converted.pinned)
        assertEquals(setOf("work", "personal"), converted.tags.map { it.display }.toSet())
        assertEquals(Instant.fromEpochMilliseconds(1_700_000_000_000L), converted.timestamp.createdAt)
        assertEquals(
            Instant.fromEpochMilliseconds(1_700_000_999_000L),
            converted.timestamp.modifiedAt,
        )
        assertNull(converted.note)
    }

    @Test
    fun `falls back to now for a null created_at`() = runTest {
        val before = Instant.fromEpochMilliseconds(System.currentTimeMillis() - 1_000)

        val converted = convert(
            legacyItem(
                detail = LegacyDetail.Password("ada", null, null, null),
                createdAt = null,
            ),
        )!!

        assertTrue(converted.timestamp.createdAt >= before)
        assertNull(converted.timestamp.modifiedAt)
    }
}
