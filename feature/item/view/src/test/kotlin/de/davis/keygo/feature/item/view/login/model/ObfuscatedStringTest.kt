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
        assertEquals("•".repeat(16), obs.hidden)
    }

    @Test
    fun `hidden preserves spaces from formatted string`() {
        val obs = ObfuscatedString(raw = "4111111111111111", formatted = "4111 1111 1111 1111")
        assertEquals("•••• •••• •••• ••••", obs.hidden)
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
            visibleSuffixDigits = 4,
        )
        assertEquals("•••• •••• •••• 1234", obs.hidden)
    }

    @Test
    fun `hidden reveals last digits for amex grouping`() {
        val obs = ObfuscatedString(
            raw = "378282246310005",
            formatted = "3782 822463 10005",
            visibleSuffixDigits = 4,
        )
        assertEquals("•••• •••••• •0005", obs.hidden)
    }

    @Test
    fun `hidden masks everything when suffix is at least total digits`() {
        val obs = ObfuscatedString(
            raw = "1234",
            formatted = "1234",
            visibleSuffixDigits = 4,
        )
        assertEquals("••••", obs.hidden)
    }
}
