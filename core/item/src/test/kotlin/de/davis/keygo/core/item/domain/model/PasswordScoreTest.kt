package de.davis.keygo.core.item.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PasswordScoreTest {

    @Test
    fun `invoke maps 1 to Ridiculous`() {
        assertEquals(Login.Score.Ridiculous, Login.Score(1))
    }

    @Test
    fun `invoke maps 2 to Weak`() {
        assertEquals(Login.Score.Weak, Login.Score(2))
    }

    @Test
    fun `invoke maps 3 to Moderate`() {
        assertEquals(Login.Score.Moderate, Login.Score(3))
    }

    @Test
    fun `invoke maps 4 to Strong`() {
        assertEquals(Login.Score.Strong, Login.Score(4))
    }

    @Test
    fun `invoke maps 5 to Excellent`() {
        assertEquals(Login.Score.Excellent, Login.Score(5))
    }

    @Test
    fun `invoke maps out of range to None`() {
        assertEquals(Login.Score.None, Login.Score(0))
        assertEquals(Login.Score.None, Login.Score(6))
        assertEquals(Login.Score.None, Login.Score(-1))
    }

    @Test
    fun `isNone returns true only for None`() {
        assertTrue(Login.Score.None.isNone)
        assertFalse(Login.Score.Ridiculous.isNone)
        assertFalse(Login.Score.Weak.isNone)
        assertFalse(Login.Score.Moderate.isNone)
        assertFalse(Login.Score.Strong.isNone)
        assertFalse(Login.Score.Excellent.isNone)
    }
}
