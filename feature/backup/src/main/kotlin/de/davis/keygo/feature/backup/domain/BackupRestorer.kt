package de.davis.keygo.feature.backup.domain

import de.davis.keygo.core.item.domain.TransactionRunner
import de.davis.keygo.core.item.domain.model.Vault
import de.davis.keygo.core.item.domain.repository.CreditCardRepository
import de.davis.keygo.core.item.domain.repository.LoginRepository
import de.davis.keygo.core.item.domain.repository.VaultRepository
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.getOrNull
import de.davis.keygo.feature.backup.domain.mapper.toUpsertCreditCard
import de.davis.keygo.feature.backup.domain.mapper.toUpsertLogin
import de.davis.keygo.feature.backup.domain.mapper.toVaultIcon
import de.davis.keygo.feature.backup.domain.model.ImportError
import de.davis.keygo.feature.backup.domain.model.ImportSummary
import de.davis.keygo.feature.backup.domain.model.ImportTarget
import de.davis.keygo.feature.item.core.domain.usecase.CreateNewOrUpdateCreditCardUseCase
import de.davis.keygo.feature.item.core.domain.usecase.CreateNewOrUpdateLoginUseCase
import de.davis.keygo.feature.vault.domain.usecase.CreateVaultUseCase
import de.davisalessandro.keygo.rust.Backup
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.Single

@Single
internal class BackupRestorer(
    private val vaultRepository: VaultRepository,
    private val loginRepository: LoginRepository,
    private val creditCardRepository: CreditCardRepository,
    private val createVault: CreateVaultUseCase,
    private val createLogin: CreateNewOrUpdateLoginUseCase,
    private val createCard: CreateNewOrUpdateCreditCardUseCase,
    private val transactionRunner: TransactionRunner,
) {

    suspend fun restore(
        backup: Backup,
        target: ImportTarget? = null,
        onProgress: suspend (processed: Int, total: Int) -> Unit,
    ): Result<ImportSummary, ImportError> {
        val total = backup.vaults.sumOf { it.logins.size + it.cards.size }
        if (total == 0) return Result.Failure(ImportError.NothingImported)

        val existingByName = vaultRepository.observeAllVaultMetadata().first()
            .associate { it.name to it.vaultId }
            .toMutableMap()

        // Persist every vault and item atomically: if the import is cancelled (e.g. the user
        // navigates back) or throws, the whole batch rolls back instead of leaving a half-imported
        // vault. The existing-vault lookup above stays outside the transaction because collecting a
        // Room Flow inside a write transaction can deadlock.
        return transactionRunner.runInTransaction {
            val acc = ImportAccumulator(total, onProgress)

            // Resolved once, ahead of the loop rather than inside it: a target collapses every
            // vault in the backup into the single one the user chose, so `New` has to create
            // exactly one vault no matter how many vaults the backup describes. That collapse is
            // also why it keeps the default icon: the sources it absorbs may disagree on one.
            val targetVaultId = when (target) {
                null -> null
                is ImportTarget.Existing -> target.vaultId
                is ImportTarget.New -> createVault(target.name, Vault.Icon.Default)
                    .getOrNull()
                    ?.also { acc.vaultCreated() }
            }

            // A vault matched by name keeps whatever icon it already has - the backup describes
            // how the vault looked when it was written, not how the user wants it to look now.
            for (bvault in backup.vaults) {
                val vaultId = if (target != null) targetVaultId
                else existingByName[bvault.name]
                    ?: createVault(bvault.name, bvault.toVaultIcon())
                        .getOrNull()
                        ?.also { existingByName[bvault.name] = it; acc.vaultCreated() }

                if (vaultId == null) {
                    acc.failAll(bvault.logins.size + bvault.cards.size)
                    continue
                }

                val loginKeys = loginRepository.getLoginsByVault(vaultId)
                    .map { it.name to it.username }.toMutableSet()
                val cardKeys = creditCardRepository.getCreditCardsByVault(vaultId)
                    .map { it.name to it.holder }.toMutableSet()

                acc.importItems(bvault.logins, loginKeys, { it.title to it.username }) {
                    createLogin(it.toUpsertLogin(vaultId))
                }
                acc.importItems(bvault.cards, cardKeys, { it.title to it.cardholder }) {
                    createCard(it.toUpsertCreditCard(vaultId))
                }
            }

            Result.Success(acc.toSummary())
        }
    }

    private class ImportAccumulator(
        private val total: Int,
        private val onProgress: suspend (processed: Int, total: Int) -> Unit,
    ) {
        private var imported = 0
        private var skipped = 0
        private var failed = 0
        private var vaultsCreated = 0
        private var processed = 0

        fun vaultCreated() {
            vaultsCreated++
        }

        suspend fun failAll(count: Int) = repeat(count) {
            failed++
            tick()
        }

        suspend fun <T> importItems(
            items: List<T>,
            existingKeys: MutableSet<Pair<String, String?>>,
            keyOf: (T) -> Pair<String, String?>,
            create: suspend (T) -> Result<*, *>,
        ) {
            for (item in items) {
                val key = keyOf(item)
                when {
                    key in existingKeys -> skipped++
                    create(item) is Result.Success -> {
                        imported++
                        existingKeys += key
                    }

                    else -> failed++
                }
                tick()
            }
        }

        fun toSummary() = ImportSummary(imported, skipped, failed, vaultsCreated)

        private suspend fun tick() {
            processed++
            onProgress(processed, total)
        }
    }
}
