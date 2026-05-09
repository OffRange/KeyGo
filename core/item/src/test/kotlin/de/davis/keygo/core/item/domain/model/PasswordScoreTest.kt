package de.davis.keygo.core.item.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PasswordScoreTest {

    @Test
    fun `invoke maps 1 to Ridiculous`() {
        assertEquals(PasswordScore.Ridiculous, PasswordScore(1))
    }

    @Test
    fun `invoke maps 2 to Weak`() {
        assertEquals(PasswordScore.Weak, PasswordScore(2))
    }

    @Test
    fun `invoke maps 3 to Moderate`() {
        assertEquals(PasswordScore.Moderate, PasswordScore(3))
    }

    @Test
    fun `invoke maps 4 to Strong`() {
        assertEquals(PasswordScore.Strong, PasswordScore(4))
    }

    @Test
    fun `invoke maps 5 to Excellent`() {
        assertEquals(PasswordScore.Excellent, PasswordScore(5))
    }

    @Test
    fun `invoke maps out of range to None`() {
        assertEquals(PasswordScore.None, PasswordScore(0))
        assertEquals(PasswordScore.None, PasswordScore(6))
        assertEquals(PasswordScore.None, PasswordScore(-1))
    }

    @Test
    fun `isNone returns true only for None`() {
        assertTrue(PasswordScore.None.isNone)
        assertFalse(PasswordScore.Ridiculous.isNone)
        assertFalse(PasswordScore.Weak.isNone)
        assertFalse(PasswordScore.Moderate.isNone)
        assertFalse(PasswordScore.Strong.isNone)
        assertFalse(PasswordScore.Excellent.isNone)
    }
}
