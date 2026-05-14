package de.davis.keygo.feature.item.core.domain.usecase

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.domain.estimator.PasswordStrengthEstimator
import de.davis.keygo.core.item.domain.model.Login
import de.davis.keygo.core.item.domain.model.PasswordCredential
import de.davis.keygo.core.item.domain.model.PasswordSecret
import de.davis.keygo.core.item.domain.model.Totp
import de.davis.keygo.core.item.domain.repository.LoginRepository
import de.davis.keygo.core.item.domain.repository.VaultRepository
import de.davis.keygo.core.item.domain.usecase.UpsertVaultItemUseCase
import de.davis.keygo.core.security.domain.crypto.CryptographicScope
import de.davis.keygo.core.security.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.security.domain.crypto.encrypt
import de.davis.keygo.core.security.domain.crypto.model.WrappedItemKeyInformation
import de.davis.keygo.core.security.domain.crypto.model.WrappedVaultKeyInformation
import de.davis.keygo.core.security.domain.crypto.wrappedItemKeyInformation
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.fold
import de.davis.keygo.core.util.mapFailure
import de.davis.keygo.core.util.resultBinding
import de.davis.keygo.feature.item.core.domain.model.FieldUpdate
import de.davis.keygo.feature.item.core.domain.model.LoginError
import de.davis.keygo.feature.item.core.domain.model.UpsertLogin
import de.davis.keygo.feature.item.core.domain.model.UpsertType
import de.davis.keygo.feature.item.core.domain.model.getValue
import de.davis.keygo.feature.item.core.domain.model.on
import de.davis.keygo.feature.item.core.domain.model.onSet
import de.davis.keygo.feature.item.core.domain.model.withoutClearingOn
import de.davis.keygo.rust.totp.TotpService
import de.davis.keygo.rust.totp.getInfoFromUriWithResult
import de.davisalessandro.keygo.rust.ItemAad
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.koin.core.annotation.Single
import kotlin.contracts.ExperimentalContracts

@Single
class CreateNewOrUpdateLoginUseCase(
    private val cryptographicScopeProvider: CryptographicScopeProvider,
    private val loginRepository: LoginRepository,
    private val vaultRepository: VaultRepository,
    private val upsertVaultItem: UpsertVaultItemUseCase,
    private val passwordStrengthEstimator: PasswordStrengthEstimator,
    private val totpService: TotpService,
) {

    @OptIn(ExperimentalContracts::class)
    private fun isValid(field: FieldUpdate<String>, allowKeep: Boolean = false): Boolean =
        when (field) {
            is FieldUpdate.Keep -> allowKeep
            is FieldUpdate.Clear -> false
            is FieldUpdate.Set<String> -> field.value.isNotBlank()
        }

    private fun validate(upsert: UpsertLogin): Set<LoginError> {
        val errors = mutableSetOf<LoginError>()
        val allowKeep = upsert.upsertType is UpsertType.Update

        if (!isValid(field = upsert.name, allowKeep = allowKeep))
            errors.add(LoginError.BlankName)

        if (!isValid(upsert.password, allowKeep = allowKeep))
            errors.add(LoginError.BlankPassword)

        return errors
    }

    suspend operator fun invoke(upsert: UpsertLogin): Result<ItemId, Set<LoginError>> {
        val errors = validate(upsert)
        if (errors.isNotEmpty()) return Result.Failure(errors)

        val updatedLogin = when (upsert.upsertType) {
            is UpsertType.Create -> buildCreate(upsert, upsert.upsertType.vaultId)
            is UpsertType.Update -> buildUpdate(
                upsert = upsert,
                id = upsert.upsertType.id,
                targetVaultId = upsert.upsertType.targetVaultId,
            )
        }

        return when (updatedLogin) {
            is Result.Success -> upsertVaultItem(updatedLogin.success).mapFailure {
                setOf(LoginError.DatabaseError(it))
            }

            is Result.Failure -> Result.Failure(setOf(updatedLogin.error))
        }
    }

    private suspend fun buildCreate(
        upsert: UpsertLogin,
        vaultId: VaultId,
    ): Result<Login, LoginError> = resultBinding {
        val itemId = newItemId()

        val vaultKeyInformation = vaultRepository.getKeyInformation(vaultId)
            ?: return Result.Failure(LoginError.InvalidVaultId)
        val aad = ItemAad(itemId = itemId, vaultId = vaultId)

        cryptographicScopeProvider.itemScope(
            wrappedVaultKeyInformation = WrappedVaultKeyInformation(
                wrappedVaultKey = vaultKeyInformation,
                vaultId = vaultId,
            ),
            wrappedItemKeyInformation = WrappedItemKeyInformation(itemAad = aad),
        ) {
            coroutineScope {
                val encryptedPassword = async {
                    PasswordSecret.encrypt(upsert.password.getValue()!!)
                }
                val totp = upsert.totoUriOrSecret.onSet { uriOrSecret ->
                    async { uriOrSecret.convertTotpUriOrSecretToUri(itemId) }
                }
                val passwordStrength = async {
                    passwordStrengthEstimator(upsert.password.getValue()!!)
                }

                val wrappedItemKey = async { wrapCurrentItemKey() }

                Login(
                    id = itemId,
                    name = upsert.name.getValue()!!,
                    username = upsert.username.getValue(),
                    domainInfos = upsert.domains.getValue().orEmpty(),
                    passwordCredential = PasswordCredential( // TODO(#43-task3)
                        secret = encryptedPassword.await(),
                        score = passwordStrength.await(),
                    ),
                    totp = totp?.await(),
                    note = upsert.note.getValue(),
                    pinned = false,
                    keyInformation = wrappedItemKey.await(),
                    vaultId = vaultId,
                )
            }
        }.bind(LoginError::CryptoError)
    }

    private suspend fun buildUpdate(
        upsert: UpsertLogin,
        id: ItemId,
        targetVaultId: VaultId?,
    ): Result<Login, LoginError> = resultBinding {
        val existing = loginRepository.getLoginById(id)
            ?: return Result.Failure(LoginError.InvalidItemId)

        val sourceVaultKeyInfo = vaultRepository.getKeyInformation(existing.vaultId)
            ?: return Result.Failure(LoginError.InvalidVaultId)
        val sourceVault = WrappedVaultKeyInformation(
            wrappedVaultKey = sourceVaultKeyInfo,
            vaultId = existing.vaultId,
        )

        val login = cryptographicScopeProvider.itemScope(
            wrappedVaultKeyInformation = sourceVault,
            wrappedItemKeyInformation = existing.wrappedItemKeyInformation(),
        ) {
            coroutineScope {
                val encryptedPassword = upsert.password.onSet { password ->
                    async { PasswordSecret.encrypt(password) }
                }
                val totp = upsert.totoUriOrSecret.onSet { uriOrSecret ->
                    async { uriOrSecret.convertTotpUriOrSecretToUri(existing.id) }
                }
                val passwordStrength = upsert.password.onSet { password ->
                    async { passwordStrengthEstimator(password) }
                }

                existing.copy(
                    name = upsert.name.withoutClearingOn(existing.name),
                    username = upsert.username.on(existing.username),
                    domainInfos = upsert.domains.on(existing.domainInfos).orEmpty(),
                    passwordCredential = PasswordCredential( // TODO(#43-task3)
                        secret = encryptedPassword?.await() ?: existing.passwordCredential!!.secret,
                        score = passwordStrength?.await() ?: existing.passwordCredential!!.score,
                    ),
                    totp = upsert.totoUriOrSecret.on(existing.totp, totp),
                    note = upsert.note.on(existing.note),
                )
            }
        }.bind(LoginError::CryptoError)

        if (targetVaultId == null || targetVaultId == existing.vaultId)
            return@resultBinding login

        // Vault changed during edit: rewrap the item key under the destination vault. Encrypted
        // secrets are bound only to the item id (see CryptographicScopeImpl.buildDataAad), so
        // they remain valid under the same item key — no re-encryption needed.
        val destinationVaultKeyInfo = vaultRepository.getKeyInformation(targetVaultId)
            ?: return Result.Failure(LoginError.InvalidVaultId)

        val rewrapped = cryptographicScopeProvider.rewrapItemKey(
            sourceVault = sourceVault,
            sourceItem = existing.wrappedItemKeyInformation(),
            destinationVault = WrappedVaultKeyInformation(
                wrappedVaultKey = destinationVaultKeyInfo,
                vaultId = targetVaultId,
            ),
        ).bind(LoginError::CryptoError)

        login.copy(
            vaultId = targetVaultId,
            keyInformation = rewrapped,
        )
    }

    context(scope: CryptographicScope)
    private suspend fun String.convertTotpUriOrSecretToUri(itemId: ItemId) = with(scope) {
        totpService.getInfoFromUriWithResult(this@convertTotpUriOrSecretToUri).fold(
            onSuccess = { secrets ->
                Totp(
                    loginId = itemId,
                    secret = Totp.Secret.encrypt(secrets.secret),
                    accountName = secrets.accountName,
                    issuer = secrets.issuer,
                    algorithm = secrets.algorithm.name.lowercase(),
                    digits = secrets.digits,
                    period = secrets.period,
                )
            },
            onFailure = {
                // not valid uri - treat it as secret
                Totp(
                    loginId = itemId,
                    secret = Totp.Secret.encrypt(this@convertTotpUriOrSecretToUri)
                )
            }
        )
    }
}
