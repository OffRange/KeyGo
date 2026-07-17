package de.davis.keygo.feature.backup.presentation.hub

import de.davis.keygo.feature.backup.domain.model.DispatchedBackup
import kotlin.test.Test
import kotlin.test.assertEquals

class BackupGroupingTest {

    private fun item(
        id: String,
        state: DispatchedBackup.State,
        timestamp: Long = 0L,
    ) = DispatchedBackup(
        id = id,
        kind = DispatchedBackup.Kind.OneTime,
        state = state,
        format = null,
        destination = null,
        progress = null,
        timestamp = timestamp,
    )

    @Test
    fun `groups appear in fixed order and skip empties`() {
        val groups = listOf(
            item("a", DispatchedBackup.State.Succeeded),
            item("b", DispatchedBackup.State.Running),
            item("c", DispatchedBackup.State.Failed),
        ).toGroups()

        assertEquals(
            listOf(
                DispatchedBackup.State.Running,
                DispatchedBackup.State.Failed,
                DispatchedBackup.State.Succeeded,
            ),
            groups.map { it.state },
        )
    }

    @Test
    fun `items within a group are newest first`() {
        val groups = listOf(
            item("old", DispatchedBackup.State.Succeeded, timestamp = 10L),
            item("new", DispatchedBackup.State.Succeeded, timestamp = 30L),
            item("mid", DispatchedBackup.State.Succeeded, timestamp = 20L),
        ).toGroups()

        assertEquals(listOf("new", "mid", "old"), groups.single().items.map { it.id })
    }
}
