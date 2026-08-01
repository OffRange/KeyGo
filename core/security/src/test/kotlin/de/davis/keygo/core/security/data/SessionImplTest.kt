package de.davis.keygo.core.security.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class SessionImplTest {

    private val session = SessionImpl()

    private fun generateArk(): ByteArray = ByteArray(32) { it.toByte() }

    @Test
    fun `dek throws when no active session`() {
        assertFailsWith<IllegalStateException> {
            session.ark
        }
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

        assertFailsWith<IllegalStateException> {
            session.ark
        }
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

    /**
     * What the v1 import's retry rests on: it is not enough for the first unlock to be announced,
     * because the run it starts is the one that may have left work behind.
     */
    @Test
    fun `a later start reaches a collector that already handled an earlier one`() = runTest {
        val starts = mutableListOf<Unit>()
        session.sessionStarts.onEach { starts += it }.launchIn(backgroundScope)

        session.startSession(generateArk())
        runCurrent()
        session.startSession(generateArk())
        runCurrent()

        assertEquals(2, starts.size)
    }

    /**
     * Replayed so the work bound to an unlock still runs when its collector attaches after the
     * fact. Nothing collecting must not be able to swallow the one signal that a v1 import, or
     * anything wired here later, gets to hear.
     */
    @Test
    fun `a start is replayed to a collector that arrives afterwards`() = runTest {
        session.startSession(generateArk())

        session.sessionStarts.first()
    }
}
