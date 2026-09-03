package de.davis.keygo.core.security.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SessionArkLifetimeTest {

    private val session = SessionImpl()

    private fun generateArk(): ByteArray = ByteArray(32) { (it + 1).toByte() }

    private class HeldArk(
        val ark: ByteArray,
        private val job: Job,
        private val resume: CompletableDeferred<Unit>,
    ) {

        suspend fun finish() {
            resume.complete(Unit)
            job.join()
        }
    }

    private fun TestScope.holdArk(): HeldArk {
        val resume = CompletableDeferred<Unit>()
        val handedOut = CompletableDeferred<ByteArray>()

        val job = launch {
            session.withArk { ark ->
                handedOut.complete(ark)
                resume.await()
            }
        }
        advanceUntilIdle()

        return HeldArk(handedOut.getCompleted(), job, resume)
    }

    @Test
    fun `a session ending does not zero an ark a suspended block still holds`() = runTest {
        // The defect this file exists for: CreateVaultUseCase and FinishExportWizardUseCase read
        // the ark, suspend, then use it. Zeroing under them persists a key wrapped with zeros.
        val expected = generateArk()
        session.startSession(expected.copyOf())

        val held = holdArk()
        session.endSession()

        assertContentEquals(expected, held.ark, "wiped while the block was still holding it")
        held.finish()
    }

    @Test
    fun `the ark is zeroed once the last in-flight block finishes`() = runTest {
        // Deferring the wipe must not cancel it: the ark still has to leave memory.
        session.startSession(generateArk())

        val held = holdArk()
        session.endSession()
        held.finish()

        assertTrue(held.ark.all { it == 0.toByte() }, "never wiped after the block finished")
    }

    @Test
    fun `the session reports itself ended at once even with a block in flight`() = runTest {
        // The UI gate keys on isActive, so it must not wait for crypto to drain.
        session.startSession(generateArk())

        val held = holdArk()
        session.endSession()

        assertEquals(false, session.isActive.value)
        held.finish()
    }

    @Test
    fun `an ark left over from a replaced session is still zeroed`() = runTest {
        // The old ark must not be forgotten in favour of the new one.
        val first = generateArk()
        session.startSession(first)

        val held = holdArk()
        session.endSession()
        session.startSession(generateArk())
        session.endSession()
        held.finish()

        assertTrue(first.all { it == 0.toByte() }, "the replaced ark was never wiped")
    }

    @Test
    fun `a block that throws still releases the ark for wiping`() = runTest {
        session.startSession(generateArk())
        val handedOut = CompletableDeferred<ByteArray>()

        runCatching {
            session.withArk { ark ->
                handedOut.complete(ark)
                error("boom")
            }
        }
        session.endSession()

        assertTrue(handedOut.await().all { it == 0.toByte() })
    }
}
