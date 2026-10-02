package de.davis.keygo.feature.password_health.data

import de.davis.keygo.core.item.domain.model.EncryptedPayload
import de.davis.keygo.core.item.domain.model.Login
import de.davis.keygo.core.item.domain.model.PasswordCredential
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.item.domain.model.PasswordSecret
import de.davis.keygo.feature.password_health.domain.model.HealthFingerprint
import de.davis.keygo.feature.password_health.domain.report.id
import de.davis.keygo.feature.password_health.domain.report.login
import kotlinx.coroutines.test.runTest
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LoginFingerprinterImplTest {

    private val fingerprinter = LoginFingerprinterImpl()

    @Test
    fun aLoginWithoutAPasswordHasNoFingerprint() = runTest {
        val login = login(id(0)).copy(passwordCredential = null)

        assertNull(fingerprinter.fingerprint(login))
    }

    @Test
    fun isTheSha256OfTheIdAndTheLengthPrefixedPayload() = runTest {
        val login = login(id(1)).withPayload(byteArrayOf(1, 2, 3), byteArrayOf(9, 8))

        val expected = MessageDigest.getInstance("SHA-256").run {
            update(ByteArray(15) + byteArrayOf(1))
            update(ByteBuffer.allocate(4).putInt(3).array())
            update(byteArrayOf(1, 2, 3))
            update(ByteBuffer.allocate(4).putInt(2).array())
            update(byteArrayOf(9, 8))
            digest()
        }

        assertEquals(HealthFingerprint(expected), fingerprinter.fingerprint(login))
    }

    @Test
    fun isStableForTheSameLogin() = runTest {
        val login = login(id(0))

        assertEquals(fingerprinter.fingerprint(login), fingerprinter.fingerprint(login))
    }

    @Test
    fun isA32ByteDigest() = runTest {
        assertEquals(32, assertNotNull(fingerprinter.fingerprint(login(id(0)))).value.size)
    }

    @Test
    fun ignoresEverythingButTheIdAndThePasswordPayload() = runTest {
        val login = login(id(0))
        val edited = login.copy(
            name = "renamed",
            username = "someone-else",
            note = "a note",
            pinned = true,
            passwordCredential = login.passwordCredential!!.copy(score = PasswordScore.Weak),
        )

        assertEquals(fingerprinter.fingerprint(login), fingerprinter.fingerprint(edited))
    }

    @Test
    fun changesWithTheCiphertext() = runTest {
        val a = login(id(0)).withPayload(byteArrayOf(1), byteArrayOf(0))
        val b = login(id(0)).withPayload(byteArrayOf(2), byteArrayOf(0))

        assertNotEquals(fingerprinter.fingerprint(a), fingerprinter.fingerprint(b))
    }

    @Test
    fun changesWithTheIv() = runTest {
        val a = login(id(0)).withPayload(byteArrayOf(1), byteArrayOf(0))
        val b = login(id(0)).withPayload(byteArrayOf(1), byteArrayOf(1))

        assertNotEquals(fingerprinter.fingerprint(a), fingerprinter.fingerprint(b))
    }

    @Test
    fun changesWithTheId() = runTest {
        val a = login(id(0)).withPayload(byteArrayOf(1), byteArrayOf(0))
        val b = login(id(1)).withPayload(byteArrayOf(1), byteArrayOf(0))

        assertNotEquals(fingerprinter.fingerprint(a), fingerprinter.fingerprint(b))
    }

    @Test
    fun movingABytePastTheCiphertextBoundaryChangesIt() = runTest {
        val a = login(id(0)).withPayload(byteArrayOf(1, 2), byteArrayOf(3))
        val b = login(id(0)).withPayload(byteArrayOf(1), byteArrayOf(2, 3))

        assertNotEquals(fingerprinter.fingerprint(a), fingerprinter.fingerprint(b))
    }

    @Test
    fun anEmptyPayloadStillHasAFingerprint() = runTest {
        val login = login(id(0)).withPayload(byteArrayOf(), byteArrayOf())

        assertNotNull(fingerprinter.fingerprint(login))
    }

    private fun Login.withPayload(ciphertext: ByteArray, iv: ByteArray) = copy(
        passwordCredential = PasswordCredential(
            secret = PasswordSecret(EncryptedPayload(ciphertext = ciphertext, iv = iv)),
            score = PasswordScore.Strong,
        ),
    )
}
