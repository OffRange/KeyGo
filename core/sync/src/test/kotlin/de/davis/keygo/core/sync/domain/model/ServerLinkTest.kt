package de.davis.keygo.core.sync.domain.model

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class ServerLinkTest {

    private fun link(serverUrl: String) = ServerLink.Connected(UUID.randomUUID(), serverUrl, "ada")

    @Test
    fun `authority drops scheme and slash`() {
        assertEquals("vault.example.org", link("https://vault.example.org/").authority)
    }

    @Test
    fun `authority keeps the port`() {
        assertEquals("10.0.2.2:8080", link("http://10.0.2.2:8080/").authority)
    }
}
