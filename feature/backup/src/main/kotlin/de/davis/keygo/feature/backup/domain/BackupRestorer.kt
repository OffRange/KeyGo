package de.davis.keygo.feature.backup.domain

import de.davis.keygo.core.item.domain.model.Vault
import de.davis.keygo.core.item.domain.repository.CreditCardRepository
import de.davis.keygo.core.item.domain.repository.LoginRepository
import de.davis.keygo.core.item.domain.repository.VaultRepository
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.getOrNull
import de.davis.keygo.feature.backup.domain.mapper.toUpsertCreditCard
import de.davis.keygo.feature.backup.domain.mapper.toUpsertLogin
import de.davis.keygo.feature.backup.domain.model.ImportError
import de.davis.keygo.feature.backup.domain.model.ImportSummary
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
) {

    suspend fun restore(
        backup: Backup,
        onProgress: suspend (processed: Int, total: Int) -> Unit,
    ): Result<ImportSummary, ImportError> {
        val total = backup.vaults.sumOf { it.logins.size + it.cards.size }
        if (total == 0) return Result.Failure(ImportError.NothingImported)

        val existingByName = vaultRepository.observeAllVaultMetadata().first()
            .associate { it.name to it.vaultId }
            .toMutableMap()

        var imported = 0
        var skipped = 0
        var failed = 0
        var vaultsCreated = 0
        var processed = 0

        for (bvault in backup.vaults) {
            val vaultId =
                existingByName[bvault.name] ?: createVault(bvault.name, Vault.Icon.Default)
                    .getOrNull()?.also { existingByName[bvault.name] = it; vaultsCreated++ }

            if (vaultId == null) {
                repeat(bvault.logins.size + bvault.cards.size) {
                    failed++
                    processed++
                    onProgress(processed, total)
                }
                continue
            }

            val loginKeys = loginRepository.getLoginsByVault(vaultId)
                .map { it.name to it.username }.toMutableSet()
            val cardKeys = creditCardRepository.getCreditCardsByVault(vaultId)
                .map { it.name to it.holder }.toMutableSet()

            for (login in bvault.logins) {
                val key = login.title to login.username
                when {
                    key in loginKeys -> skipped++
                    createLogin(login.toUpsertLogin(vaultId)) is Result.Success -> {
                        imported++
                        loginKeys += key
                    }

                    else -> failed++
                }
                processed++
                onProgress(processed, total)
            }

            for (card in bvault.cards) {
                val key = card.title to card.cardholder
                when {
                    key in cardKeys -> skipped++
                    createCard(card.toUpsertCreditCard(vaultId)) is Result.Success -> {
                        imported++
                        cardKeys += key
                    }

                    else -> failed++
                }
                processed++
                onProgress(processed, total)
            }
        }

        return Result.Success(ImportSummary(imported, skipped, failed, vaultsCreated))
    }
}
