package de.davis.keygo.core.security.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class SessionImplTest {

    private val session = SessionImpl()

    private fun generateArk(): ByteArray = ByteArray(32) { it.toByte() }

    @Test
    fun `no ark is handed out when there is no active session`() = runTest {
        assertNull(session.withArk { it })
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
    fun `startSession makes the ark available`() = runTest {
        val key = generateArk()
        session.startSession(key)
        assertSame(key, session.withArk { it })
    }

    @Test
    fun `endSession clears the ark`() = runTest {
        session.startSession(generateArk())
        session.endSession()

        assertNull(session.withArk { it })
    }

    @Test
    fun `startSession replaces previous session`() = runTest {
        val key1 = generateArk()
        val key2 = generateArk()

        session.startSession(key1)
        session.startSession(key2)

        assertSame(key2, session.withArk { it })
    }

    @Test
    fun `startSession wipes the ark it replaces`() {
        val replaced = generateArk()
        session.startSession(replaced)
        session.startSession(generateArk())

        assertContentEquals(ByteArray(32), replaced)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `startSession pulses isActive through false when replacing an already-active session`() =
        runTest(UnconfinedTestDispatcher()) {
            // A plain `.value = true` no-ops on an already-true StateFlow. Collectors keyed on the
            // false transition (e.g. wiping a typed password on lock) must still see it fire here.
            // Unconfined so the collector resumes inline, the way a real Dispatchers.Main.immediate
            // collector would - a queuing dispatcher would conflate both writes before it ever runs.
            session.startSession(generateArk())

            val collected = mutableListOf<Boolean>()
            val job = launch { session.isActive.collect { collected.add(it) } }

            session.startSession(generateArk())

            job.cancel()
            assertEquals(listOf(true, false, true), collected)
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
