package de.davis.keygo.core.sync.presentation

import de.davis.keygo.core.sync.R
import de.davis.keygo.core.sync.domain.model.NetworkFailure
import de.davis.keygo.core.sync.domain.model.SyncError
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncErrorMessageTest {

    @Test
    fun network_failures_keep_their_cause() {
        val cases = mapOf(
            NetworkFailure.UnknownHost to R.string.sync_error_unknown_host,
            NetworkFailure.Timeout to R.string.sync_error_timeout,
            NetworkFailure.Refused to R.string.sync_error_refused,
            NetworkFailure.Unreachable to R.string.sync_error_unreachable,
            NetworkFailure.CleartextBlocked to R.string.sync_error_insecure,
            NetworkFailure.UntrustedCertificate to R.string.sync_error_untrusted_certificate,
            NetworkFailure.Tls to R.string.sync_error_tls_failed,
            NetworkFailure.Other to R.string.sync_error_unreachable,
        )

        cases.forEach { (kind, expected) ->
            assertEquals(expected, SyncError.Network(kind).messageRes, "for $kind")
        }
    }

    @Test
    fun server_responses_tell_a_foreign_server_from_a_broken_one() {
        val cases = mapOf(
            404 to R.string.sync_error_not_keygo,
            301 to R.string.sync_error_redirected,
            502 to R.string.sync_error_unavailable,
            503 to R.string.sync_error_unavailable,
            500 to R.string.sync_error_server,
        )

        cases.forEach { (status, expected) ->
            assertEquals(
                expected,
                SyncError.UnexpectedStatus(status, "<html>").messageRes,
                "for $status",
            )
        }
        assertEquals(R.string.sync_error_not_keygo, SyncError.InvalidResponse("not json").messageRes)
        assertEquals(R.string.sync_error_server, SyncError.Server("internal", "detail").messageRes)
    }

    @Test
    fun a_broken_keystore_does_not_read_as_a_bad_address() {
        assertEquals(R.string.sync_error_unknown, SyncError.Internal("keystore locked").messageRes)
    }
}
