package de.davis.keygo.item.core.domain.usecase

import de.davis.keygo.core.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.domain.estimator.PasswordStrengthEstimator
import de.davis.keygo.core.domain.model.Password
import de.davis.keygo.core.domain.model.asTotpSecret
import de.davis.keygo.core.domain.repository.PasswordRepository
import de.davis.keygo.core.domain.usecase.UpsertVaultItem
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.mapFailure
import de.davis.keygo.item.core.domain.model.FieldUpdate
import de.davis.keygo.item.core.domain.model.PasswordError
import de.davis.keygo.item.core.domain.model.Upsert
import de.davis.keygo.item.core.domain.model.getValue
import de.davis.keygo.item.core.domain.model.on
import de.davis.keygo.item.core.domain.model.onSet
import de.davis.keygo.item.core.domain.model.withoutClearingOn
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.koin.core.annotation.Single
import kotlin.contracts.ExperimentalContracts

@Single
class CreateNewOrUpdatePasswordUseCase(
    private val cryptographicScopeProvider: CryptographicScopeProvider,
    private val passwordRepository: PasswordRepository,
    private val upsertVaultItem: UpsertVaultItem,
    private val passwordStrengthEstimator: PasswordStrengthEstimator
) {

    @OptIn(ExperimentalContracts::class)
    private fun isValid(field: FieldUpdate<String>, allowKeep: Boolean = false): Boolean =
        when (field) {
            is FieldUpdate.Keep -> allowKeep
            is FieldUpdate.Clear -> false
            is FieldUpdate.Set<String> -> field.value.isNotBlank()
        }

    private fun validate(upsert: Upsert): Set<PasswordError> {
        val errors = mutableSetOf<PasswordError>()
        val allowKeep = upsert is Upsert.Update

        if (!isValid(field = upsert.name, allowKeep = allowKeep))
            errors.add(PasswordError.BlankName)

        if (!isValid(upsert.password, allowKeep = allowKeep))
            errors.add(PasswordError.BlankPassword)

        return errors
    }

    suspend operator fun invoke(upsert: Upsert): Result<Unit, Set<PasswordError>> =
        coroutineScope {
            val errors = validate(upsert)
            if (errors.isNotEmpty())
                return@coroutineScope Result.Failure(errors)


            val encryptedPassword = upsert.password.onSet { password ->
                async {
                    cryptographicScopeProvider.scope {
                        password.encodeToByteArray().encrypt()
                    }
                }
            }

            val passwordStrength = upsert.password.onSet { password ->
                async { passwordStrengthEstimator(password) }
            }


            val totpSecret = upsert.totpSecret.onSet { totpSecret ->
                async {
                    cryptographicScopeProvider.scope {
                        totpSecret.encodeToByteArray().encrypt()
                    }.asTotpSecret()
                }
            }

            val updatedPassword = when (upsert) {
                is Upsert.Create -> {
                    // Validation ensures that the values are not null
                    Password(
                        name = upsert.name.getValue() ?: "",
                        username = upsert.username.getValue(),
                        website = upsert.website.getValue(),
                        encryptedData = encryptedPassword!!.await(),
                        totpSecret = totpSecret?.await(),
                        score = passwordStrength!!.await(),
                        note = upsert.note.getValue(),
                    )
                }

                is Upsert.Update -> {
                    val dbPassword = passwordRepository.getVaultPasswordById(upsert.vaultId)
                        ?: return@coroutineScope Result.Failure(setOf(PasswordError.InvalidVaultId))

                    dbPassword.copy(
                        name = upsert.name.withoutClearingOn(dbPassword.name),
                        username = upsert.username.on(dbPassword.username),
                        website = upsert.website.on(dbPassword.website),
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
