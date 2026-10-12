package de.davis.keygo.core.security.domain.crypto

import de.davis.keygo.core.item.domain.model.SecretKind
import de.davis.keygo.core.security.domain.crypto.model.SealedAccountRecord
import de.davis.keygo.core.security.domain.model.AccountRecordError
import de.davis.keygo.core.util.Result
import java.util.UUID

/**
 * Seals account-wide records that belong to no vault, such as the password-health report. Each
 * record has its own key, wrapped under the ARK and bound to the record's namespace.
 *
 * A namespace is part of its record's on-disk identity: changing it makes the record unreadable.
 */
interface AccountRecordSealer {

    /** Seals under a fresh key, so a record never depends on an older one still opening. */
    suspend fun seal(
        namespace: UUID,
        kind: SecretKind,
        plaintext: ByteArray,
    ): Result<SealedAccountRecord, AccountRecordError>

    suspend fun open(
        namespace: UUID,
        kind: SecretKind,
        record: SealedAccountRecord,
    ): Result<ByteArray, AccountRecordError>
}
