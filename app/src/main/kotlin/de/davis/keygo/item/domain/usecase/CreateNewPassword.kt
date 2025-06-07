package de.davis.keygo.item.domain.usecase

import de.davis.keygo.core.domain.Result
import de.davis.keygo.core.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.domain.model.Password
import de.davis.keygo.item.domain.model.PasswordError
import org.koin.core.annotation.Single

@Single
class CreateNewPassword(
    private val cryptographicScopeProvider: CryptographicScopeProvider
) {

    suspend operator fun invoke(
        name: String,
        username: String,
        website: String,
        password: String,
        note: String
    ): Result<Password, List<PasswordError>> {
        val nameError = if (name.isBlank()) PasswordError.BlankName else null
        val passwordError = if (password.isBlank()) PasswordError.BlankPassword else null

        if (nameError != null || passwordError != null) {
            return Result.Failure(listOfNotNull(nameError, passwordError))
        }

        val generatedPassword = cryptographicScopeProvider.scope {
            Password(
                name = name,
                username = username.takeIf { it.isNotBlank() },
                website = website.takeIf { it.isNotBlank() },
                encryptedData = password.encodeToByteArray().encrypt(),
                note = note.takeIf { it.isNotBlank() },
            )
        }

        return Result.Success(generatedPassword)
    }
}
