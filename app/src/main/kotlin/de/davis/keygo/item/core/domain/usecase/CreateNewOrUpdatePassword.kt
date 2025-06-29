package de.davis.keygo.item.core.domain.usecase

import de.davis.keygo.core.domain.Result
import de.davis.keygo.core.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.domain.mapFailure
import de.davis.keygo.core.domain.model.Password
import de.davis.keygo.core.domain.repository.PasswordRepository
import de.davis.keygo.core.domain.usecase.EstimatePasswordStrengthUseCase
import de.davis.keygo.core.domain.usecase.UpsertVaultItem
import de.davis.keygo.item.core.domain.model.PasswordError
import de.davis.keygo.item.core.domain.model.Upsert
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.koin.core.annotation.Single

@Single
class CreateNewOrUpdatePassword(
    private val cryptographicScopeProvider: CryptographicScopeProvider,
    private val passwordRepository: PasswordRepository,
    private val upsertVaultItem: UpsertVaultItem,
    private val estimatePasswordStrength: EstimatePasswordStrengthUseCase
) {

    suspend operator fun invoke(upsert: Upsert): Result<Unit, List<PasswordError>> =
        coroutineScope {
            val nameError = if (upsert.name?.isBlank() == true) PasswordError.BlankName else null
            val passwordError =
                if (upsert.password?.isBlank() == true) PasswordError.BlankPassword else null

            if (nameError != null || passwordError != null) {
                return@coroutineScope Result.Failure(listOfNotNull(nameError, passwordError))
            }


            val encryptedPassword = upsert.password?.let {
                async {
                    cryptographicScopeProvider.scope {
                        it.encodeToByteArray().encrypt()
                    }
                }
            }

            val passwordStrength = upsert.password?.let { async { estimatePasswordStrength(it) } }

            val updatedPassword = when (upsert) {
                is Upsert.Create -> {
                    Password(
                        name = upsert.name,
                        username = upsert.username?.takeIf { it.isNotBlank() },
                        website = upsert.website?.takeIf { it.isNotBlank() },
                        encryptedData = encryptedPassword!!.await(),
                        score = passwordStrength!!.await(),
                        note = upsert.note?.takeIf { it.isNotBlank() },
                    )
                }

                is Upsert.Update -> {
                    val dbPassword = passwordRepository.getVaultPasswordById(upsert.vaultId)
                        ?: return@coroutineScope Result.Failure(listOf(PasswordError.InvalidVaultId))

                    dbPassword.copy(
                        name = upsert.name ?: dbPassword.name,
                        username = upsert.username ?: dbPassword.username,
                        website = upsert.website ?: dbPassword.website,
                        encryptedData = encryptedPassword?.await() ?: dbPassword.encryptedData,
                        score = passwordStrength?.await() ?: dbPassword.score,
                        note = upsert.note ?: dbPassword.note,
                    )
                }
            }

            upsertVaultItem(updatedPassword).mapFailure {
                listOf(PasswordError.DatabaseError(it))
            }
        }
}