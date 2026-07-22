package de.davis.keygo.feature.backup.data.reository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackupJobRetentionTest {

    private fun retained(vararg finishedAt: Pair<String, Long?>, max: Int) =
        retainedJobKeys(finishedAt.toMap(), max = max, recurringKey = "recurring")

    @Test
    fun `keeps the recurring record even when it is the oldest finished job`() {
        val keep = retained("recurring" to 1L, "a" to 100L, "b" to 200L, max = 1)

        assertTrue("recurring" in keep)
    }

    @Test
    fun `keeps unfinished jobs regardless of the cap`() {
        val keep = retained("pending" to null, "a" to 100L, "b" to 200L, max = 1)

        assertTrue("pending" in keep)
    }

    @Test
    fun `caps finished one-time jobs to the most recent`() {
        val keep = retained("old" to 100L, "mid" to 200L, "new" to 300L, max = 2)

        assertEquals(setOf("mid", "new"), keep)
    }

    @Test
    fun `evicts nothing when under the cap`() {
        val keep = retained("a" to 100L, "b" to 200L, max = 5)

        assertEquals(setOf("a", "b"), keep)
    }
}
