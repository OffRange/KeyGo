package de.davis.keygo.feature.backup.domain

import de.davis.keygo.core.item.domain.model.CreditCard
import de.davis.keygo.core.item.domain.model.Item
import de.davis.keygo.core.item.domain.model.Login
import de.davis.keygo.core.item.domain.model.VaultMetadata
import de.davis.keygo.core.item.domain.repository.CreditCardRepository
import de.davis.keygo.core.item.domain.repository.LoginRepository
import de.davis.keygo.core.item.domain.repository.PasskeyRepository
import de.davis.keygo.core.item.domain.repository.VaultRepository
import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.security.domain.crypto.CryptographicScope
import de.davis.keygo.core.security.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.security.domain.crypto.CryptographicScopeProviderFactory
import de.davis.keygo.core.security.domain.crypto.model.WrappedVaultKeyInformation
import de.davis.keygo.core.security.domain.crypto.wrappedItemKeyInformation
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.ResultBinding
import de.davis.keygo.core.util.asResult
import de.davis.keygo.core.util.resultBinding
import de.davis.keygo.feature.backup.domain.mapper.toBackupCard
import de.davis.keygo.feature.backup.domain.mapper.toBackupIcon
import de.davis.keygo.feature.backup.domain.mapper.toBackupLogin
import de.davis.keygo.feature.backup.domain.mapper.toExportError
import de.davis.keygo.feature.backup.domain.model.CollectedBackup
import de.davis.keygo.feature.backup.domain.model.ExportError
import de.davisalessandro.keygo.rust.Backup
import de.davisalessandro.keygo.rust.BackupVault
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single

@Single
internal class BackupCollector(
    private val vaultRepository: VaultRepository,
    private val loginRepository: LoginRepository,
    private val creditCardRepository: CreditCardRepository,
    private val passkeyRepository: PasskeyRepository,
    private val scopeProviderFactory: CryptographicScopeProviderFactory,
) {

    private data class VaultItems(
        val meta: VaultMetadata,
        val logins: List<Login>,
        val cards: List<CreditCard>,
    ) {
        val items get() = logins.size + cards.size
    }

    private class ItemExporter(
        private val scopeProvider: CryptographicScopeProvider,
        private val total: Int,
        private val onProgress: suspend (processed: Int, total: Int) -> Unit,
    ) {
        private var processed = 0
        private val progressMutex = Mutex()

        context(binder: ResultBinding<ExportError>)
        suspend fun <I : Item, R> export(
            item: I,
            vaultKey: WrappedVaultKeyInformation,
            map: suspend CryptographicScope.(I) -> R,
        ): R = with(binder) {
            scopeProvider.itemScope(vaultKey, item.wrappedItemKeyInformation()) { map(item) }
                .bind { it.toExportError() }
                .also { progressMutex.withLock { onProgress(++processed, total) } }
        }
    }

    suspend fun collect(
        session: Session,
        onProgress: suspend (processed: Int, total: Int) -> Unit,
    ): Result<CollectedBackup, ExportError> = resultBinding {
        val perVault = coroutineScope {
            vaultRepository.observeAllVaultMetadata().first().map { meta ->
                async {
                    val logins = async { loginRepository.getLoginsByVault(meta.vaultId) }
                    val cards = async { creditCardRepository.getCreditCardsByVault(meta.vaultId) }
                    VaultItems(
                        meta = meta,
                        logins = logins.await(),
                        cards = cards.await(),
                    )
                }
            }.awaitAll()
        }

        val total = perVault.sumOf { it.items }
        (total > 0).asResult(ExportError.NothingToExport).bind()

        val exporter = ItemExporter(
            scopeProvider = scopeProviderFactory.forSession(session),
            total = total,
            onProgress = onProgress,
        )

        val backupVaults = perVault.map { (meta, logins, cards) ->
            // Every item here was fetched by this vault's id, so one lookup serves all of them.
            val vaultKey = WrappedVaultKeyInformation(
                wrappedVaultKey = vaultRepository.getKeyInformation(meta.vaultId)
                    .asResult(ExportError.CryptoFailed).bind(),
                vaultId = meta.vaultId,
            )

            val (exportedLogins, exportedCards) = coroutineScope {
                val loginResults = logins.map { login ->
                    async {
                        val passkeys = passkeyRepository.getPasskeysByLogin(login.id)
                        exporter.export(login, vaultKey) { it.toBackupLogin(passkeys) }
                    }
                }
                val cardResults = cards.map { card ->
                    async { exporter.export(card, vaultKey) { it.toBackupCard() } }
                }
                loginResults.awaitAll() to cardResults.awaitAll()
            }

            BackupVault(
                name = meta.name,
                icon = meta.icon.toBackupIcon(),
                logins = exportedLogins,
                cards = exportedCards,
            )
        }

        CollectedBackup(Backup(backupVaults), total)
    }
}
