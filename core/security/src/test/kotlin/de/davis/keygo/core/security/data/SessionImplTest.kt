package de.davis.keygo.core.security.data

import de.davis.keygo.core.security.domain.crypto.model.AesKey
import javax.crypto.KeyGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class SessionImplTest {

    private val session = SessionImpl()

    private fun generateAesKey(): AesKey =
        AesKey(KeyGenerator.getInstance("AES").apply { init(256) }.generateKey())

    @Test
    fun `dek throws when no active session`() {
        assertFailsWith<IllegalStateException> {
            session.dek
        }
    }

    @Test
    fun `startSession makes dek available`() {
        val key = generateAesKey()
        session.startSession(key)
        assertEquals(key, session.dek)
    }

    @Test
    fun `endSession clears dek`() {
        session.startSession(generateAesKey())
        session.endSession()

        assertFailsWith<IllegalStateException> {
            session.dek
        }
    }

    @Test
    fun `startSession replaces previous session`() {
        val key1 = generateAesKey()
        val key2 = generateAesKey()

        session.startSession(key1)
        session.startSession(key2)

        assertNotEquals(key1, session.dek)
        assertEquals(key2, session.dek)
    }

    @Test
    fun `endSession is safe to call without active session`() {
        session.endSession() // should not throw
    }

    @Test
    fun `endSession is safe to call multiple times`() {
        session.startSession(generateAesKey())
        session.endSession()
        session.endSession() // should not throw
    }
}
