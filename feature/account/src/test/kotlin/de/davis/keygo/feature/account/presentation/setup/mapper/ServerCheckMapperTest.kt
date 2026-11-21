package de.davis.keygo.feature.account.presentation.setup.mapper

import de.davis.keygo.core.sync.domain.model.ServerInfo
import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.account.domain.model.ServerAddress
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerCheckMapperTest {

    @Test
    fun invite_only_is_the_inverse_of_open_signups() {
        val address = (ServerAddress.parse("vault.example.org") as Result.Success).success

        assertTrue(ServerInfo("1.0", "https://v", allowSignups = false).toFound(address).inviteOnly)
        assertFalse(ServerInfo("1.0", "https://v", allowSignups = true).toFound(address).inviteOnly)
    }
}
