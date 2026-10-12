package de.davis.keygo.core.security.data.crypto

import de.davis.keygo.core.item.domain.model.SecretKind
import de.davis.keygo.core.security.FakeSession
import de.davis.keygo.core.security.domain.crypto.model.SealedAccountRecord
import de.davis.keygo.core.security.domain.model.AccountRecordError
import de.davis.keygo.core.util.assertFailure
import de.davis.keygo.core.util.assertSuccess
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AccountRecordSealerImplTest {

    private val session = FakeSession(startUnlocked = true)
    private val sealer = AccountRecordSealerImpl(session)

    @Test
    fun `a sealed record opens to its plaintext`() = runTest {
        val record = sealer.seal(NAMESPACE, KIND, PLAINTEXT).assertSuccess()

        assertContentEquals(PLAINTEXT, sealer.open(NAMESPACE, KIND, record).assertSuccess())
    }

    @Test
    fun `every seal uses a fresh key`() = runTest {
        val first = sealer.seal(NAMESPACE, KIND, PLAINTEXT).assertSuccess()
        val second = sealer.seal(NAMESPACE, KIND, PLAINTEXT).assertSuccess()

        assertFalse(first.wrappedKey.wrappedKey.contentEquals(second.wrappedKey.wrappedKey))
    }

    @Test
    fun `a record under another namespace is a key failure`() = runTest {
        val record = sealer.seal(NAMESPACE, KIND, PLAINTEXT).assertSuccess()

        assertEquals(
            AccountRecordError.KeyUnwrapFailed,
            sealer.open(UUID.randomUUID(), KIND, record).assertFailure(),
        )
    }

    @Test
    fun `a record opened as another kind is corrupted`() = runTest {
        val record = sealer.seal(NAMESPACE, KIND, PLAINTEXT).assertSuccess()

        assertEquals(
            AccountRecordError.Corrupted,
            sealer.open(NAMESPACE, SecretKind.Password, record).assertFailure(),
        )
    }

    @Test
    fun `tampered data is corrupted`() = runTest {
        val record = sealer.seal(NAMESPACE, KIND, PLAINTEXT).assertSuccess()
        val tampered = SealedAccountRecord(
            wrappedKey = record.wrappedKey,
            data = record.data.copy(
                data = record.data.data.copyOf().also { it[0] = (it[0] + 1).toByte() },
            ),
        )

        assertEquals(
            AccountRecordError.Corrupted,
            sealer.open(NAMESPACE, KIND, tampered).assertFailure(),
        )
    }

    @Test
    fun `a locked session can neither seal nor open`() = runTest {
        val record = sealer.seal(NAMESPACE, KIND, PLAINTEXT).assertSuccess()
        session.endSession()

        assertEquals(
            AccountRecordError.NoActiveSession,
            sealer.seal(NAMESPACE, KIND, PLAINTEXT).assertFailure(),
        )
        assertEquals(
            AccountRecordError.NoActiveSession,
            sealer.open(NAMESPACE, KIND, record).assertFailure(),
        )
    }

    private companion object {
        val NAMESPACE: UUID = UUID.fromString("39050172-ce6d-4f4d-8787-6e12ab44eb0e")
        val KIND = SecretKind.PasswordHealthReport
        val PLAINTEXT = byteArrayOf(1, 2, 3, 4)
    }
}
