package de.davis.keygo.core.sync.data

import de.davis.keygo.core.sync.data.local.model.ProtoSessionStore
import de.davis.keygo.core.sync.domain.model.ServerLink
import de.davisalessandro.keygo.rust.StoredSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TokenStoreTest {

    private val dataStore = FakeDataStore(ProtoSessionStore.getDefaultInstance())
    private val store = TokenStore(dataStore)

    private val session = StoredSession(
        serverUrl = "https://vault.example.org/",
        userId = UUID.randomUUID(),
        username = "ada",
        deviceId = UUID.randomUUID(),
        refreshToken = "kgr_token",
        refreshExpiresAt = 99u,
    )

    @Test
    fun `a saved session loads back unchanged`() = runTest {
        store.save(session)

        assertEquals(session, store.load())
    }

    @Test
    fun `a saved session is observed as the server link`() = runTest {
        store.save(session)

        assertEquals(
            ServerLink.Connected(
                userId = session.userId,
                serverUrl = "https://vault.example.org/",
                username = "ada",
            ),
            store.observeLink().first(),
        )
    }

    @Test
    fun `clearing the session removes the link`() = runTest {
        store.save(session)

        store.clear()

        assertNull(store.load())
        assertEquals(ServerLink.None, store.observeLink().first())
    }
}
