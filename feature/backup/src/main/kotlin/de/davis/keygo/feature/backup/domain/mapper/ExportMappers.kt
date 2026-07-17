package de.davis.keygo.feature.backup.domain.mapper

import de.davis.keygo.core.item.domain.model.CreditCard
import de.davis.keygo.core.item.domain.model.Login
import de.davis.keygo.core.item.domain.model.Passkey
import de.davis.keygo.core.security.domain.crypto.CryptographicScope
import de.davis.keygo.core.security.domain.crypto.decrypt
import de.davis.keygo.feature.backup.domain.model.CsvPreset
import de.davisalessandro.keygo.rust.BackupCard
import de.davisalessandro.keygo.rust.BackupLogin
import de.davisalessandro.keygo.rust.BackupPasskey
import de.davisalessandro.keygo.rust.ExportPreset

context(scope: CryptographicScope)
internal suspend fun Login.toBackupLogin(passkeys: List<Passkey>): BackupLogin = BackupLogin(
    title = name,
    notes = note,
    tags = tags.map { it.display },
    pinned = pinned,
    username = username,
    password = passwordCredential?.secret?.decrypt(),
    totpSecret = totp?.secret?.decrypt(),
    website = domainInfos.firstOrNull()?.value,
    passkeys = passkeys.map { it.toBackupPasskey() },
)

// The private key is sealed under the login item's key (AAD = loginId + vaultId), so it opens in
// that login's scope - the same one this mapper already runs in.
context(scope: CryptographicScope)
private suspend fun Passkey.toBackupPasskey(): BackupPasskey = BackupPasskey(
    userName = user.name,
    userDisplayName = user.displayName,
    credentialId = credentialId,
    privateKey = privateKey.decrypt(),
    rp = rp,
)

context(scope: CryptographicScope)
internal suspend fun CreditCard.toBackupCard(): BackupCard = BackupCard(
    title = name,
    notes = note,
    tags = tags.map { it.display },
    pinned = pinned,
    cardholder = holder,
    number = cardNumber?.decrypt() ?: "",
    expirationMonth = expirationDate?.monthValue?.toUByte(),
    expirationYear = expirationDate?.year?.toUShort(),
    cvv = cvv?.decrypt(),
)

internal fun CsvPreset.toRust(): ExportPreset = when (this) {
    CsvPreset.KeyGo -> ExportPreset.KEY_GO
    CsvPreset.Browser -> ExportPreset.BROWSER
}
