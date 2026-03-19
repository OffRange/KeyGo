package de.davis.keygo.core.item.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PasswordScoreTest {

    @Test
    fun `invoke maps 1 to Ridiculous`() {
        assertEquals(Password.Score.Ridiculous, Password.Score(1))
    }

    @Test
    fun `invoke maps 2 to Weak`() {
        assertEquals(Password.Score.Weak, Password.Score(2))
    }

    @Test
    fun `invoke maps 3 to Moderate`() {
        assertEquals(Password.Score.Moderate, Password.Score(3))
    }

    @Test
    fun `invoke maps 4 to Strong`() {
        assertEquals(Password.Score.Strong, Password.Score(4))
    }

    @Test
    fun `invoke maps 5 to Excellent`() {
        assertEquals(Password.Score.Excellent, Password.Score(5))
    }

    @Test
    fun `invoke maps out of range to None`() {
        assertEquals(Password.Score.None, Password.Score(0))
        assertEquals(Password.Score.None, Password.Score(6))
        assertEquals(Password.Score.None, Password.Score(-1))
    }

    @Test
    fun `isNone returns true only for None`() {
        assertTrue(Password.Score.None.isNone)
        assertFalse(Password.Score.Ridiculous.isNone)
        assertFalse(Password.Score.Weak.isNone)
        assertFalse(Password.Score.Moderate.isNone)
        assertFalse(Password.Score.Strong.isNone)
        assertFalse(Password.Score.Excellent.isNone)
    }
}
