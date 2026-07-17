package de.davis.keygo.feature.backup.domain

import de.davis.keygo.core.item.domain.model.CreditCard
import de.davis.keygo.core.item.domain.model.Item
import de.davis.keygo.core.item.domain.model.Login
import de.davis.keygo.core.item.domain.model.VaultMetadata
import de.davis.keygo.core.item.domain.repository.CreditCardRepository
import de.davis.keygo.core.item.domain.repository.LoginRepository
import de.davis.keygo.core.item.domain.repository.PasskeyRepository
import de.davis.keygo.core.item.domain.repository.VaultRepository
import de.davis.keygo.core.security.domain.crypto.CryptographicScope
import de.davis.keygo.core.security.domain.usecase.ItemWithCryptoScopeUseCase
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.asResult
import de.davis.keygo.core.util.resultBinding
import de.davis.keygo.feature.backup.domain.mapper.toBackupCard
import de.davis.keygo.feature.backup.domain.mapper.toBackupLogin
import de.davis.keygo.feature.backup.domain.model.CollectedBackup
import de.davis.keygo.feature.backup.domain.model.ExportError
import de.davisalessandro.keygo.rust.Backup
import de.davisalessandro.keygo.rust.BackupVault
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.Single

@Single
internal class BackupCollector(
    private val vaultRepository: VaultRepository,
    private val loginRepository: LoginRepository,
    private val creditCardRepository: CreditCardRepository,
    private val passkeyRepository: PasskeyRepository,
    private val arkUnlocker: BackupArkUnlocker,
) {

    private data class VaultItems(
        val meta: VaultMetadata,
        val logins: List<Login>,
        val cards: List<CreditCard>,
    ) {
        val items get() = logins.size + cards.size
    }

    suspend fun collect(
        onProgress: suspend (processed: Int, total: Int) -> Unit,
    ): Result<CollectedBackup, ExportError> = resultBinding {
        arkUnlocker.withScope { scope -> collectWith(scope, onProgress).bind() }.bind()
    }

    private suspend fun collectWith(
        scope: ItemWithCryptoScopeUseCase,
        onProgress: suspend (processed: Int, total: Int) -> Unit,
    ): Result<CollectedBackup, ExportError> = resultBinding {
        val perVault = coroutineScope {
            vaultRepository.observeAllVaultMetadata().first().map { meta ->
                val logins = async { loginRepository.getLoginsByVault(meta.vaultId) }
                val cards = async { creditCardRepository.getCreditCardsByVault(meta.vaultId) }
                VaultItems(
                    meta = meta,
                    logins = logins.await(),
                    cards = cards.await(),
                )
            }
        }

        val total = perVault.sumOf { it.items }
        (total > 0).asResult(ExportError.NothingToExport).bind()

        var processed = 0
        suspend fun <I : Item, R> I.export(map: suspend CryptographicScope.(I) -> R): R =
            scope.withItem(this, map)
                .bind { ExportError.CryptoFailed }
                .also { onProgress(++processed, total) }

        val backupVaults = perVault.map { (meta, logins, cards) ->
            BackupVault(
                name = meta.name,
                logins = logins.map { login ->
                    val passkeys = passkeyRepository.getPasskeysByLogin(login.id)
                    login.export { it.toBackupLogin(passkeys) }
                },
                cards = cards.map { it.export { card -> card.toBackupCard() } },
            )
        }

        CollectedBackup(Backup(backupVaults), total)
    }
}
