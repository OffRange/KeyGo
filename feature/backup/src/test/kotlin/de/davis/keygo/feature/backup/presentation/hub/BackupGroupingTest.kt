package de.davis.keygo.feature.backup.presentation.hub

import de.davis.keygo.feature.backup.domain.model.DispatchedBackup
import de.davis.keygo.feature.backup.presentation.hub.model.BackupSection
import kotlin.test.Test
import kotlin.test.assertEquals

class BackupGroupingTest {

    private fun item(
        id: String,
        state: DispatchedBackup.State,
        kind: DispatchedBackup.Kind = DispatchedBackup.Kind.OneTime,
        timestamp: Long = 0L,
    ) = DispatchedBackup(
        id = id,
        kind = kind,
        state = state,
        format = null,
        destination = null,
        timestamp = timestamp,
    )

    @Test
    fun `sections appear in fixed order and skip empties`() {
        val groups = listOf(
            item("a", DispatchedBackup.State.Succeeded),
            item("b", DispatchedBackup.State.Enqueued, DispatchedBackup.Kind.Recurring),
            item("c", DispatchedBackup.State.Running()),
        ).toGroups()

        assertEquals(
            listOf(BackupSection.InProgress, BackupSection.Scheduled, BackupSection.Recent),
            groups.map { it.section },
        )
    }

    @Test
    fun `empty sections are omitted`() {
        val groups = listOf(item("a", DispatchedBackup.State.Failed)).toGroups()

        assertEquals(listOf(BackupSection.Recent), groups.map { it.section })
    }

    @Test
    fun `an enqueued recurring backup is scheduled but a running one is in progress`() {
        val groups = listOf(
            item("waiting", DispatchedBackup.State.Enqueued, DispatchedBackup.Kind.Recurring),
            item("working", DispatchedBackup.State.Running(), DispatchedBackup.Kind.Recurring),
        ).toGroups()

        assertEquals(
            listOf(BackupSection.InProgress, BackupSection.Scheduled),
            groups.map { it.section },
        )
        assertEquals(listOf("working"), groups.first().items.map { it.id })
        assertEquals(listOf("waiting"), groups.last().items.map { it.id })
    }

    @Test
    fun `an enqueued one-time backup is in progress`() {
        val groups = listOf(
            item("queued", DispatchedBackup.State.Enqueued, DispatchedBackup.Kind.OneTime),
        ).toGroups()

        assertEquals(listOf(BackupSection.InProgress), groups.map { it.section })
    }

    @Test
    fun `succeeded failed and cancelled all land in recent`() {
        val groups = listOf(
            item("s", DispatchedBackup.State.Succeeded),
            item("f", DispatchedBackup.State.Failed),
            item("c", DispatchedBackup.State.Cancelled),
        ).toGroups()

        assertEquals(listOf(BackupSection.Recent), groups.map { it.section })
        assertEquals(3, groups.single().items.size)
    }

    @Test
    fun `items within a section are newest first`() {
        val groups = listOf(
            item("old", DispatchedBackup.State.Succeeded, timestamp = 10L),
            item("new", DispatchedBackup.State.Succeeded, timestamp = 30L),
            item("mid", DispatchedBackup.State.Succeeded, timestamp = 20L),
        ).toGroups()

        assertEquals(listOf("new", "mid", "old"), groups.single().items.map { it.id })
    }
}
