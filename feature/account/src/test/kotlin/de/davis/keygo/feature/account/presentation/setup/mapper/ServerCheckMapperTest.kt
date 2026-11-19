package de.davis.keygo.feature.account.presentation.setup.mapper

import de.davis.keygo.core.sync.domain.model.NetworkFailure
import de.davis.keygo.core.sync.domain.model.ServerInfo
import de.davis.keygo.core.sync.domain.model.SyncError
import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.account.domain.model.ServerAddress
import de.davis.keygo.feature.account.presentation.setup.model.ServerCheck.Failed.Reason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerCheckMapperTest {

    @Test
    fun network_failures_keep_their_cause() {
        val cases = mapOf(
            NetworkFailure.UnknownHost to Reason.UnknownHost,
            NetworkFailure.Timeout to Reason.Timeout,
            NetworkFailure.Refused to Reason.Refused,
            NetworkFailure.Unreachable to Reason.Unreachable,
            NetworkFailure.CleartextBlocked to Reason.InsecureConnection,
            NetworkFailure.UntrustedCertificate to Reason.UntrustedCertificate,
            NetworkFailure.Tls to Reason.SecureConnectionFailed,
            NetworkFailure.Other to Reason.Unreachable,
        )

        cases.forEach { (kind, expected) ->
            assertEquals(expected, SyncError.Network(kind).toFailureReason(), "for $kind")
        }
    }

    @Test
    fun server_responses_tell_a_foreign_server_from_a_broken_one() {
        val cases = mapOf(
            404 to Reason.NotKeyGo,
            301 to Reason.Redirected,
            502 to Reason.ServerUnavailable,
            503 to Reason.ServerUnavailable,
            500 to Reason.ServerError,
        )

        cases.forEach { (status, expected) ->
            assertEquals(
                expected,
                SyncError.UnexpectedStatus(status, "<html>").toFailureReason(),
                "for $status",
            )
        }
        assertEquals(Reason.NotKeyGo, SyncError.InvalidResponse("not json").toFailureReason())
        assertEquals(Reason.ServerError, SyncError.Server("internal", "detail").toFailureReason())
    }

    @Test
    fun other_errors() {
        assertEquals(Reason.InvalidAddress, SyncError.InvalidUrl("bad url").toFailureReason())
        assertEquals(Reason.RateLimited, SyncError.RateLimited.toFailureReason())
        assertEquals(Reason.Unknown, SyncError.VaultLocked.toFailureReason())
        // A broken keystore must not read as a typo in the address.
        assertEquals(Reason.Unknown, SyncError.Internal("keystore locked").toFailureReason())
    }

    @Test
    fun invite_only_is_the_inverse_of_open_signups() {
        val address = (ServerAddress.parse("vault.example.org") as Result.Success).success

        assertTrue(ServerInfo("1.0", "https://v", allowSignups = false).toFound(address).inviteOnly)
        assertFalse(ServerInfo("1.0", "https://v", allowSignups = true).toFound(address).inviteOnly)
    }
}
