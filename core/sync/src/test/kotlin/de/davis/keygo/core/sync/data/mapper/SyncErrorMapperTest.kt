package de.davis.keygo.core.sync.data.mapper

import de.davis.keygo.core.sync.domain.model.NetworkFailure
import de.davis.keygo.core.sync.domain.model.SyncError
import de.davisalessandro.keygo.rust.NetworkException
import de.davisalessandro.keygo.rust.SyncException
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncErrorMapperTest {

    @Test
    fun network_error_keeps_its_kind() {
        val error = SyncException.Network(NetworkException.UntrustedCertificate())

        assertEquals(SyncError.Network(NetworkFailure.UntrustedCertificate), error.toSyncError())
    }

    @Test
    fun every_rust_kind_maps_to_its_domain_kind() {
        val cases = mapOf(
            NetworkException.UnknownHost() to NetworkFailure.UnknownHost,
            NetworkException.Timeout() to NetworkFailure.Timeout,
            NetworkException.Refused() to NetworkFailure.Refused,
            NetworkException.Unreachable() to NetworkFailure.Unreachable,
            NetworkException.CleartextBlocked() to NetworkFailure.CleartextBlocked,
            NetworkException.UntrustedCertificate() to NetworkFailure.UntrustedCertificate,
            NetworkException.Tls() to NetworkFailure.Tls,
            NetworkException.Other() to NetworkFailure.Other,
        )

        assertEquals(NetworkFailure.entries.toSet(), cases.values.toSet())
        cases.forEach { (kind, expected) ->
            assertEquals(SyncError.Network(expected), SyncException.Network(kind).toSyncError())
        }
    }

    @Test
    fun foreign_responses_keep_their_shape() {
        assertEquals(
            SyncError.UnexpectedStatus(502, "<html>"),
            SyncException.UnexpectedStatus(502.toUShort(), "<html>").toSyncError(),
        )
        assertEquals(
            SyncError.InvalidResponse("not json"),
            SyncException.InvalidResponse("not json").toSyncError(),
        )
    }

    @Test
    fun internal_and_invalid_url_keep_their_reason() {
        assertEquals(SyncError.Internal("boom"), SyncException.Internal("boom").toSyncError())
        assertEquals(SyncError.InvalidUrl("bad"), SyncException.InvalidUrl("bad").toSyncError())
    }
}
