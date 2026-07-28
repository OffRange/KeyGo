package de.davis.keygo.migration.legacy_data.data.json

import de.davis.keygo.migration.legacy_data.domain.model.LegacyDetail
import de.davis.keygo.migration.legacy_data.domain.model.LegacyStrength
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Every fixture here is shaped the way GSON wrote it in v1, including the details that look wrong:
 * `password` is a JSON array of signed bytes because GSON serialises `byte[]` that way, and
 * `strength` appears both as an enum name and as the older `{"type": n}` object because v1's
 * `ElementDetailTypeAdapter` rewrote the object form on read and left already-migrated rows alone.
 */
class LegacyDetailParserTest {

    private val parser = LegacyDetailParser()

    private fun parse(json: String) = parser.parse(json.encodeToByteArray())

    @Test
    fun `parses a password with a string strength`() {
        val detail = parse(
            """{"type":1,"username":"ada","origin":"https://example.com",
               "password":[1,-2,3],"strength":"STRONG"}""",
        )

        val password = assertIs<LegacyDetail.Password>(detail)
        assertEquals("ada", password.username)
        assertEquals("https://example.com", password.origin)
        assertContentEquals(byteArrayOf(1, -2, 3), password.password)
        assertEquals(LegacyStrength.STRONG, password.strength)
    }

    @Test
    fun `parses a password with the legacy object strength`() {
        val detail = parse("""{"type":1,"username":"ada","strength":{"type":3}}""")

        assertEquals(LegacyStrength.STRONG, assertIs<LegacyDetail.Password>(detail).strength)
    }

    @Test
    fun `parses a password with no strength at all`() {
        val detail = parse("""{"type":1,"username":"ada"}""")

        assertNull(assertIs<LegacyDetail.Password>(detail).strength)
    }

    @Test
    fun `maps every legacy strength ordinal`() {
        val expected = listOf(
            LegacyStrength.RIDICULOUS,
            LegacyStrength.WEAK,
            LegacyStrength.MODERATE,
            LegacyStrength.STRONG,
            LegacyStrength.VERY_STRONG,
        )

        expected.forEachIndexed { ordinal, strength ->
            val detail = parse("""{"type":1,"strength":{"type":$ordinal}}""")
            assertEquals(strength, assertIs<LegacyDetail.Password>(detail).strength)
        }
    }

    @Test
    fun `keeps a password whose strength object is not shaped like v1 wrote it`() {
        val detail = parse("""{"type":1,"username":"ada","strength":{"type":{"nested":1}}}""")

        val password = assertIs<LegacyDetail.Password>(detail)
        assertEquals("ada", password.username)
        assertNull(password.strength)
    }

    @Test
    fun `parses a credit card`() {
        val detail = parse(
            """{"type":17,"cardholder":{"firstName":"Ada","lastName":"Lovelace"},
               "expirationDate":"04/29","cardNumber":"4111111111111111","cvv":"123"}""",
        )

        val card = assertIs<LegacyDetail.CreditCard>(detail)
        assertEquals("Ada", card.firstName)
        assertEquals("Lovelace", card.lastName)
        assertEquals("04/29", card.expirationDate)
        assertEquals("4111111111111111", card.cardNumber)
        assertEquals("123", card.cvv)
    }

    @Test
    fun `parses a credit card with a null cardholder and blank cvv`() {
        val detail = parse("""{"type":17,"cardholder":null,"cvv":"","cardNumber":"4111"}""")

        val card = assertIs<LegacyDetail.CreditCard>(detail)
        assertNull(card.firstName)
        assertNull(card.lastName)
        assertEquals("", card.cvv)
    }

    @Test
    fun `tolerates unknown keys that a newer v1 build might have written`() {
        val detail = parse("""{"type":1,"username":"ada","somethingNew":{"a":1}}""")

        assertEquals("ada", assertIs<LegacyDetail.Password>(detail).username)
    }

    @Test
    fun `returns null for an unknown type id`() {
        assertNull(parse("""{"type":99,"username":"ada"}"""))
    }

    @Test
    fun `returns null when the type is missing`() {
        assertNull(parse("""{"username":"ada"}"""))
    }

    @Test
    fun `returns null for malformed json`() {
        assertNull(parse("not json at all"))
    }

    @Test
    fun `returns null for empty input`() {
        assertNull(parser.parse(byteArrayOf()))
    }
}
