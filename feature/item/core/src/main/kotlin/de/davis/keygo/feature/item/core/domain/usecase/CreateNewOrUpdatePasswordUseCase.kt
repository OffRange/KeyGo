package de.davis.keygo.feature.item.core.domain.usecase

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.domain.estimator.PasswordStrengthEstimator
import de.davis.keygo.core.item.domain.model.Password
import de.davis.keygo.core.item.domain.model.Password.Companion.LABEL_PASSWORD
import de.davis.keygo.core.item.domain.model.Password.Companion.LABEL_TOTP_SECRET
import de.davis.keygo.core.item.domain.repository.PasswordRepository
import de.davis.keygo.core.item.domain.repository.VaultRepository
import de.davis.keygo.core.item.domain.usecase.UpsertVaultItemUseCase
import de.davis.keygo.core.security.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.security.domain.crypto.encryptSecretData
import de.davis.keygo.core.security.domain.crypto.model.WrappedItemKeyInformation
import de.davis.keygo.core.security.domain.crypto.model.WrappedVaultKeyInformation
import de.davis.keygo.core.security.domain.crypto.wrappedItemKeyInformation
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.mapFailure
import de.davis.keygo.feature.item.core.domain.model.FieldUpdate
import de.davis.keygo.feature.item.core.domain.model.PasswordError
import de.davis.keygo.feature.item.core.domain.model.UpsertPassword
import de.davis.keygo.feature.item.core.domain.model.UpsertType
import de.davis.keygo.feature.item.core.domain.model.getValue
import de.davis.keygo.feature.item.core.domain.model.on
import de.davis.keygo.feature.item.core.domain.model.onSet
import de.davis.keygo.feature.item.core.domain.model.withoutClearingOn
import de.davisalessandro.keygo.rust.ItemAad
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.koin.core.annotation.Single
import kotlin.contracts.ExperimentalContracts

@Single
class CreateNewOrUpdatePasswordUseCase(
    private val cryptographicScopeProvider: CryptographicScopeProvider,
    private val passwordRepository: PasswordRepository,
    private val vaultRepository: VaultRepository,
    private val upsertVaultItem: UpsertVaultItemUseCase,
    private val passwordStrengthEstimator: PasswordStrengthEstimator
) {

    @OptIn(ExperimentalContracts::class)
    private fun isValid(field: FieldUpdate<String>, allowKeep: Boolean = false): Boolean =
        when (field) {
            is FieldUpdate.Keep -> allowKeep
            is FieldUpdate.Clear -> false
            is FieldUpdate.Set<String> -> field.value.isNotBlank()
        }

    private fun validate(upsert: UpsertPassword): Set<PasswordError> {
        val errors = mutableSetOf<PasswordError>()
        val allowKeep = upsert.upsertType is UpsertType.Update

        if (!isValid(field = upsert.name, allowKeep = allowKeep))
            errors.add(PasswordError.BlankName)

        if (!isValid(upsert.password, allowKeep = allowKeep))
            errors.add(PasswordError.BlankPassword)

        return errors
    }

    suspend operator fun invoke(upsert: UpsertPassword): Result<ItemId, Set<PasswordError>> {
        val errors = validate(upsert)
        if (errors.isNotEmpty()) return Result.Failure(errors)

        val updatedPassword = when (upsert.upsertType) {
            is UpsertType.Create -> buildCreate(upsert, upsert.upsertType.vaultId)
            is UpsertType.Update -> buildUpdate(upsert, upsert.upsertType.id)
        }

        return when (updatedPassword) {
            is Result.Success -> upsertVaultItem(updatedPassword.success).mapFailure {
                setOf(PasswordError.DatabaseError(it))
            }

            is Result.Failure -> Result.Failure(setOf(updatedPassword.error))
        }
    }

    private suspend fun buildCreate(
        upsert: UpsertPassword,
        vaultId: VaultId
    ): Result<Password, PasswordError> {
        val itemId = newItemId()

        val vaultKeyInformation = vaultRepository.getKeyInformation(vaultId)
            ?: return Result.Failure(PasswordError.InvalidVaultId)
        val aad = ItemAad(itemId = itemId, vaultId = vaultId)

        val password = cryptographicScopeProvider.itemScope(
            wrappedVaultKeyInformation = WrappedVaultKeyInformation(
                wrappedVaultKey = vaultKeyInformation,
                vaultId = vaultId
            ),
            wrappedItemKeyInformation = WrappedItemKeyInformation(itemAad = aad),
        ) {
            coroutineScope {
                val encryptedPassword = async {
                    upsert.password.getValue()!!.encryptSecretData(label = LABEL_PASSWORD)
                }
                val encryptedTotp = upsert.totpSecret.onSet { secret ->
                    async { secret.encryptSecretData(label = LABEL_TOTP_SECRET) }
                }
                val passwordStrength = async {
                    passwordStrengthEstimator(upsert.password.getValue()!!)
                }

                val wrappedItemKey = async { wrapCurrentItemKey() }

                Password(
                    id = itemId,
                    name = upsert.name.getValue()!!,
                    username = upsert.username.getValue(),
                    domainInfos = upsert.domains.getValue().orEmpty(),
                    password = encryptedPassword.await(),
                    totpSecret = encryptedTotp?.await(),
                    score = passwordStrength.await(),
                    note = upsert.note.getValue(),
                    pinned = false,
                    keyInformation = wrappedItemKey.await(),
                    vaultId = vaultId,
                )
            }
        }

        return Result.Success(password)
    }

    private suspend fun buildUpdate(
        upsert: UpsertPassword,
        id: ItemId
    ): Result<Password, PasswordError> {
        val existing = passwordRepository.getPasswordById(id)
            ?: return Result.Failure(PasswordError.InvalidItemId)

        val vaultKeyInfo = vaultRepository.getKeyInformation(existing.vaultId)
            ?: return Result.Failure(PasswordError.InvalidVaultId)

        val password = cryptographicScopeProvider.itemScope(
            wrappedVaultKeyInformation = WrappedVaultKeyInformation(
                wrappedVaultKey = vaultKeyInfo,
                vaultId = existing.vaultId
            ),
            wrappedItemKeyInformation = existing.wrappedItemKeyInformation(),
        ) {
            coroutineScope {
                val encryptedPassword = upsert.password.onSet { password ->
                    async { password.encryptSecretData(label = LABEL_PASSWORD) }
                }
                val totpSecret = upsert.totpSecret.onSet { secret ->
                    async { secret.encryptSecretData(label = LABEL_TOTP_SECRET) }
                }
                val passwordStrength = upsert.password.onSet { password ->
                    async { passwordStrengthEstimator(password) }
                }

                existing.copy(
                    name = upsert.name.withoutClearingOn(existing.name),
                    username = upsert.username.on(existing.username),
                    domainInfos = upsert.domains.on(existing.domainInfos).orEmpty(),
                    password = encryptedPassword?.await() ?: existing.password,
                    totpSecret = upsert.totpSecret.on(existing.totpSecret, totpSecret),
                    score = passwordStrength?.await() ?: existing.score,
                    note = upsert.note.on(existing.note),
                )
            }
        }

        return Result.Success(password)
    }
}
