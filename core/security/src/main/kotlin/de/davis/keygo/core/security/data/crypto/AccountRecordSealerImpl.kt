package de.davis.keygo.core.security.data.crypto

import de.davis.keygo.core.item.domain.model.SecretKind
import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.security.domain.SessionError
import de.davis.keygo.core.security.domain.crypto.AccountRecordSealer
import de.davis.keygo.core.security.domain.crypto.model.CryptographicData
import de.davis.keygo.core.security.domain.crypto.model.SealedAccountRecord
import de.davis.keygo.core.security.domain.model.AccountRecordError
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.mapFailure
import de.davis.keygo.core.util.mapSuccess
import de.davisalessandro.keygo.rust.AccountRecord
import de.davisalessandro.keygo.rust.EncryptedItemBlob
import org.koin.core.annotation.Single
import java.util.UUID

@Single
internal class AccountRecordSealerImpl(
    private val session: Session,
) : AccountRecordSealer {

    override suspend fun seal(
        namespace: UUID,
        kind: SecretKind,
        plaintext: ByteArray,
    ): Result<SealedAccountRecord, AccountRecordError> =
        session.sealAccountRecord(
            namespace = namespace,
            kind = kind.toFfi(),
            plaintext = plaintext,
        ).mapSuccess { it.toSealedAccountRecord() }.mapFailure { it.toAccountRecordError() }

    override suspend fun open(
        namespace: UUID,
        kind: SecretKind,
        record: SealedAccountRecord,
    ): Result<ByteArray, AccountRecordError> =
        session.openAccountRecord(
            namespace = namespace,
            kind = kind.toFfi(),
            record = record.toAccountRecord(),
        ).mapFailure { it.toAccountRecordError() }
}

private fun SessionError.toAccountRecordError(): AccountRecordError = when (this) {
    SessionError.Locked -> AccountRecordError.NoActiveSession
    SessionError.DecryptionFailed -> AccountRecordError.Corrupted
    SessionError.WrongPassword, is SessionError.Derivation, is SessionError.KeyWrap ->
        AccountRecordError.KeyUnwrapFailed
}

private fun AccountRecord.toSealedAccountRecord() = SealedAccountRecord(
    wrappedKey = wrappedKey.toKeyInformation(),
    data = CryptographicData(data = data.ciphertext, iv = data.nonce),
)

private fun SealedAccountRecord.toAccountRecord() = AccountRecord(
    wrappedKey = wrappedKey.toWrappedKeyBlob(),
    data = EncryptedItemBlob(ciphertext = data.data, nonce = data.iv),
)
