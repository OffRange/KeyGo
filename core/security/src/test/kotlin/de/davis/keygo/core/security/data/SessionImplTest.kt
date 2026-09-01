package de.davis.keygo.core.security.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class SessionImplTest {

    private val session = SessionImpl()

    private fun generateArk(): ByteArray = ByteArray(32) { it.toByte() }

    @Test
    fun `dek is null when no active session`() {
        assertNull(session.ark)
    }

    @Test
    fun `isActive is false when no active session`() {
        assertEquals(false, session.isActive.value)
    }

    @Test
    fun `isActive is true after startSession`() {
        session.startSession(generateArk())
        assertEquals(true, session.isActive.value)
    }

    @Test
    fun `isActive is false after endSession`() {
        session.startSession(generateArk())
        session.endSession()
        assertEquals(false, session.isActive.value)
    }

    @Test
    fun `startSession makes dek available`() {
        val key = generateArk()
        session.startSession(key)
        assertEquals(key, session.ark)
    }

    @Test
    fun `endSession clears dek`() {
        session.startSession(generateArk())
        session.endSession()

        assertNull(session.ark)
    }

    @Test
    fun `startSession replaces previous session`() {
        val key1 = generateArk()
        val key2 = generateArk()

        session.startSession(key1)
        session.startSession(key2)

        assertNotEquals(key1, session.ark)
        assertEquals(key2, session.ark)
    }

    @Test
    fun `endSession is safe to call without active session`() {
        session.endSession() // should not throw
    }

    @Test
    fun `endSession is safe to call multiple times`() {
        session.startSession(generateArk())
        session.endSession()
        session.endSession() // should not throw
    }
}
