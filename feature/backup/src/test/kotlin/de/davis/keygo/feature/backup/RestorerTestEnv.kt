package de.davis.keygo.feature.backup

import de.davis.keygo.core.item.FakeCreditCardRepository
import de.davis.keygo.core.item.FakeItemRepository
import de.davis.keygo.core.item.FakeLoginRepository
import de.davis.keygo.core.item.FakePasswordStrengthEstimator
import de.davis.keygo.core.item.FakeTransactionRunner
import de.davis.keygo.core.item.FakeVaultContextRepository
import de.davis.keygo.core.item.FakeVaultRepository
import de.davis.keygo.core.item.domain.usecase.UpsertVaultItemUseCase
import de.davis.keygo.core.security.crypto.FakeCryptographicScopeProvider
import de.davis.keygo.core.security.crypto.FakeSession
import de.davis.keygo.feature.backup.domain.BackupRestorer
import de.davis.keygo.feature.item.core.domain.usecase.CreateNewOrUpdateCreditCardUseCase
import de.davis.keygo.feature.item.core.domain.usecase.CreateNewOrUpdateLoginUseCase
import de.davis.keygo.feature.vault.domain.usecase.CreateVaultUseCase
import de.davis.keygo.rust.FakeCardFormatter
import de.davis.keygo.rust.FakeKeyWrapper
import de.davis.keygo.rust.FakeTotpService
import de.davis.keygo.rust.FakeVaultManager

internal class RestorerTestEnv {
    val vaultRepo = FakeVaultRepository()
    val loginRepo = FakeLoginRepository()
    val cardRepo = FakeCreditCardRepository()
    val transactionRunner = FakeTransactionRunner()
    private val scope = FakeCryptographicScopeProvider(FakeItemRepository())
    private val upsert = UpsertVaultItemUseCase(loginRepo, cardRepo)

    private val createLogin = CreateNewOrUpdateLoginUseCase(
        cryptographicScopeProvider = scope,
        loginRepository = loginRepo,
        vaultRepository = vaultRepo,
        upsertVaultItem = upsert,
        passwordStrengthEstimator = FakePasswordStrengthEstimator(),
        totpService = FakeTotpService(),
    )
    private val createCard = CreateNewOrUpdateCreditCardUseCase(
        creditCardRepository = cardRepo,
        cardFormatter = FakeCardFormatter(),
        cryptographicScopeProvider = scope,
        vaultRepository = vaultRepo,
        upsertVaultItem = upsert,
    )
    private val createVault = CreateVaultUseCase(
        vaultRepository = vaultRepo,
        vaultContextRepository = FakeVaultContextRepository(),
        vaultManager = FakeVaultManager(),
        keyWrapper = FakeKeyWrapper(),
        session = FakeSession(startOnConstruct = true),
    )

    val restorer = BackupRestorer(
        vaultRepository = vaultRepo,
        loginRepository = loginRepo,
        creditCardRepository = cardRepo,
        createVault = createVault,
        createLogin = createLogin,
        createCard = createCard,
        transactionRunner = transactionRunner,
    )
}
