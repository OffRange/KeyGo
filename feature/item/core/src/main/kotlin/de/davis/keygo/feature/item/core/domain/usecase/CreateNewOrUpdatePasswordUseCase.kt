package de.davis.keygo.feature.item.core.domain.usecase

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.crypto.encryptSecretData
import de.davis.keygo.core.item.domain.estimator.PasswordStrengthEstimator
import de.davis.keygo.core.item.domain.model.Password
import de.davis.keygo.core.item.domain.repository.PasswordRepository
import de.davis.keygo.core.item.domain.usecase.UpsertVaultItemUseCase
import de.davis.keygo.core.security.domain.crypto.CryptographicScopeProvider
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.koin.core.annotation.Single
import kotlin.contracts.ExperimentalContracts

@Single
class CreateNewOrUpdatePasswordUseCase(
    private val cryptographicScopeProvider: CryptographicScopeProvider,
    private val passwordRepository: PasswordRepository,
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

    suspend operator fun invoke(upsert: UpsertPassword): Result<ItemId, Set<PasswordError>> =
        coroutineScope {
            val errors = validate(upsert)
            if (errors.isNotEmpty())
                return@coroutineScope Result.Failure(errors)


            val encryptedPassword = upsert.password.onSet { password ->
                async {
                    cryptographicScopeProvider.scope {
                        password.encryptSecretData()
                    }
                }
            }

            val passwordStrength = upsert.password.onSet { password ->
                async { passwordStrengthEstimator(password) }
            }


            val totpSecret = upsert.totpSecret.onSet { totpSecret ->
                async {
                    cryptographicScopeProvider.scope {
                        totpSecret.encryptSecretData()
                    }
                }
            }

            val updatedPassword = when (upsert.upsertType) {
                UpsertType.Create -> {
                    // Validation ensures that the values are not null
                    Password(
                        name = upsert.name.getValue() ?: "",
                        username = upsert.username.getValue(),
                        domainInfos = upsert.domains.getValue().orEmpty(),
                        encryptedData = encryptedPassword!!.await(),
                        totpSecret = totpSecret?.await(),
                        score = passwordStrength!!.await(),
                        note = upsert.note.getValue(),
                    )
                }

                is UpsertType.Update -> {
                    val dbPassword =
                        passwordRepository.getPasswordById(upsert.upsertType.vaultItemId)
                            ?: return@coroutineScope Result.Failure(setOf(PasswordError.InvalidVaultId))

                    dbPassword.copy(
                        name = upsert.name.withoutClearingOn(dbPassword.name),
                        username = upsert.username.on(dbPassword.username),
                        domainInfos = upsert.domains.on(dbPassword.domainInfos).orEmpty(),
                        encryptedData = encryptedPassword?.await() ?: dbPassword.encryptedData,
                        totpSecret = upsert.totpSecret.on(dbPassword.totpSecret, totpSecret),
                        score = passwordStrength?.await() ?: dbPassword.score,
                        note = upsert.note.on(dbPassword.note),
                    )
                }
            }

            upsertVaultItem(updatedPassword).mapFailure {
                setOf(PasswordError.DatabaseError(it))
            }
        }
}
