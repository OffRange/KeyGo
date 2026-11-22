package de.davis.keygo.feature.account.presentation.mapper

import de.davis.keygo.core.sync.domain.model.ServerLink
import de.davis.keygo.feature.account.presentation.models.AccountState
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class AccountStateMapperTest {

    private fun link(serverUrl: String) = ServerLink(UUID.randomUUID(), serverUrl, "ada")

    @Test
    fun `no link is a local account`() {
        assertEquals(AccountState.Local, null.toAccountState())
    }

    @Test
    fun `a link shows the server without scheme or slash`() {
        assertEquals(
            AccountState.Connected(username = "ada", server = "vault.example.org"),
            link("https://vault.example.org/").toAccountState(),
        )
    }

    @Test
    fun `a port stays part of the server`() {
        assertEquals(
            AccountState.Connected(username = "ada", server = "10.0.2.2:8080"),
            link("http://10.0.2.2:8080/").toAccountState(),
        )
    }
}
