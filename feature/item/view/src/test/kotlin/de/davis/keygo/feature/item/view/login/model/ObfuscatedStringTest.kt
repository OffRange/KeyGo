package de.davis.keygo.feature.item.view.login.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ObfuscatedStringTest {

    @Test
    fun `formatted defaults to raw`() {
        val obs = ObfuscatedString("4111111111111111")
        assertEquals("4111111111111111", obs.formatted)
    }

    @Test
    fun `hidden is all bullets when no formatted string given`() {
        val obs = ObfuscatedString("4111111111111111")
        assertEquals(mask("*".repeat(16)), obs.hidden)
    }

    @Test
    fun `hidden preserves spaces from formatted string when configured`() {
        val obs = ObfuscatedString(
            raw = "4111111111111111",
            formatted = "4111 1111 1111 1111",
            preservedChars = setOf(' '),
        )
        assertEquals(mask("**** **** **** ****"), obs.hidden)
    }

    @Test
    fun `hidden obfuscates spaces by default`() {
        val obs = ObfuscatedString(raw = "4111111111111111", formatted = "4111 1111 1111 1111")
        assertEquals(mask("****" + "*".repeat(15)), obs.hidden)
    }

    @Test
    fun `hidden obfuscates letters and symbols, not just digits`() {
        val obs = ObfuscatedString("Sup3r\$ecret!")
        assertEquals(mask("*".repeat(12)), obs.hidden)
    }

    @Test
    fun `hidden preserves only chars configured via preservedChars`() {
        val obs = ObfuscatedString(
            raw = "555-01-2345",
            formatted = "555-01-2345",
            preservedChars = setOf('-'),
        )
        assertEquals(mask("***-**-****"), obs.hidden)
    }

    @Test
    fun `hidden reveals suffix among preserved chars for mixed content`() {
        val obs = ObfuscatedString(
            raw = "555-01-2345",
            formatted = "555-01-2345",
            visibleSuffixChars = 3,
            preservedChars = setOf('-'),
        )
        assertEquals(mask("***-**-*345"), obs.hidden)
    }

    @Test
    fun `raw is unaffected by formatted`() {
        val obs = ObfuscatedString(raw = "4111111111111111", formatted = "4111 1111 1111 1111")
        assertEquals("4111111111111111", obs.raw)
    }

    @Test
    fun `hidden reveals last digits for visa grouping`() {
        val obs = ObfuscatedString(
            raw = "4111111111111234",
            formatted = "4111 1111 1111 1234",
            visibleSuffixChars = 4,
            preservedChars = setOf(' '),
        )
        assertEquals(mask("**** **** **** 1234"), obs.hidden)
    }

    @Test
    fun `hidden reveals last digits for amex grouping`() {
        val obs = ObfuscatedString(
            raw = "378282246310005",
            formatted = "3782 822463 10005",
            visibleSuffixChars = 4,
            preservedChars = setOf(' '),
        )
        assertEquals(mask("**** ****** *0005"), obs.hidden)
    }

    @Test
    fun `hidden masks everything when suffix is at least total digits`() {
        val obs = ObfuscatedString(
            raw = "1234",
            formatted = "1234",
            visibleSuffixChars = 4,
        )
        assertEquals(mask("****"), obs.hidden)
    }
}

// Expected values are written with "*" so this file stays ASCII; the real glyph is U+2022.
private fun mask(pattern: String): String = pattern.replace('*', '\u2022')
