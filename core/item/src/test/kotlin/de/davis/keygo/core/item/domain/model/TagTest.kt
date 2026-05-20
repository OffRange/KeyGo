package de.davis.keygo.core.item.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TagTest {

    @Test
    fun `of trims surrounding whitespace and preserves inner casing`() {
        val tag = Tag.of("  Banking  ")

        assertEquals("Banking", tag?.display)
    }

    @Test
    fun `normalized is the lowercased display`() {
        val tag = Tag.of("BankING")!!

        assertEquals("banking", tag.normalized)
    }

    @Test
    fun `of returns null for an empty string`() {
        assertNull(Tag.of(""))
    }

    @Test
    fun `of returns null for whitespace-only input`() {
        assertNull(Tag.of("   "))
    }

    @Test
    fun `normalize lowercases and trims independently of constructing a Tag`() {
        assertEquals("banking", Tag.normalize("  BankING "))
    }

    @Test
    fun `equality is case-insensitive on the trimmed value`() {
        assertEquals(Tag.of("Bank")!!, Tag.of("bank")!!)
        assertEquals(Tag.of("Bank")!!, Tag.of("  BANK ")!!)
    }

    @Test
    fun `hashCode is the same for tags equal under normalization`() {
        assertEquals(Tag.of("Bank")!!.hashCode(), Tag.of("bank")!!.hashCode())
    }

    @Test
    fun `different normalized values are not equal`() {
        assertNotEquals(Tag.of("Bank")!!, Tag.of("Work")!!)
    }

    @Test
    fun `Set of Tag dedupes by normalized form and keeps the first display seen`() {
        val tags = setOf(Tag.of("Bank")!!, Tag.of("bank")!!, Tag.of("BANK")!!)

        assertEquals(1, tags.size)
        assertEquals("Bank", tags.single().display)
    }

    @Test
    fun `Tag is not equal to a String with the same value`() {
        assertTrue(Tag.of("Bank")!!.equals("Bank").not())
    }

    @Test
    fun `toString returns the display form`() {
        assertEquals("Banking", Tag.of("Banking")!!.toString())
    }
}
