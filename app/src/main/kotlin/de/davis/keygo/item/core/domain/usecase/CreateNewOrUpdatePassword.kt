package de.davis.keygo.item.core.domain.usecase

import de.davis.keygo.core.domain.Result
import de.davis.keygo.core.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.domain.mapFailure
import de.davis.keygo.core.domain.model.Password
import de.davis.keygo.core.domain.repository.PasswordRepository
import de.davis.keygo.core.domain.usecase.UpsertVaultItem
import de.davis.keygo.item.core.domain.model.PasswordError
import de.davis.keygo.item.core.domain.model.Upsert
import org.koin.core.annotation.Single

@Single
class CreateNewOrUpdatePassword(
    private val cryptographicScopeProvider: CryptographicScopeProvider,
    private val passwordRepository: PasswordRepository,
    private val upsertVaultItem: UpsertVaultItem,
) {

    suspend operator fun invoke(upsert: Upsert): Result<Unit, List<PasswordError>> {
        val nameError = if (upsert.name?.isBlank() == true) PasswordError.BlankName else null
        val passwordError =
            if (upsert.password?.isBlank() == true) PasswordError.BlankPassword else null

        if (nameError != null || passwordError != null) {
            return Result.Failure(listOfNotNull(nameError, passwordError))
        }

        val updatedPassword = when (upsert) {
            is Upsert.Create -> {
                Password(
                    name = upsert.name,
                    username = upsert.username?.takeIf { it.isNotBlank() },
                    website = upsert.website?.takeIf { it.isNotBlank() },
                    encryptedData = cryptographicScopeProvider.scope {
                        upsert.password.encodeToByteArray().encrypt()
                    },
                    note = upsert.note?.takeIf { it.isNotBlank() },
                )
            }

            is Upsert.Update -> {
                val dbPassword = passwordRepository.getVaultPasswordById(upsert.vaultId)
                    ?: return Result.Failure(listOf(PasswordError.InvalidVaultId))

                dbPassword.copy(
                    name = upsert.name ?: dbPassword.name,
                    username = upsert.username ?: dbPassword.username,
                    website = upsert.website ?: dbPassword.website,
                    encryptedData = cryptographicScopeProvider.scope {
                        upsert.password?.encodeToByteArray()?.encrypt()
                    } ?: dbPassword.encryptedData,
                    note = upsert.note ?: dbPassword.note,
                )
            }
        }

        return upsertVaultItem(updatedPassword).mapFailure {
            listOf(PasswordError.DatabaseError(it))
        }
    }
}