package de.davis.keygo.rust.username

import de.davis.keygo.core.util.Result
import de.davisalessandro.keygo.rust.UsernameException
import de.davisalessandro.keygo.rust.UsernameValidatorInterface

typealias UsernameValidator = UsernameValidatorInterface

sealed interface UsernameError {
    data class TooShort(val minLength: Int) : UsernameError
    data class TooLong(val maxLength: Int) : UsernameError
    data class InvalidCharacter(val character: String) : UsernameError
    data object InvalidStart : UsernameError
    data object InvalidEnd : UsernameError
    data object ConsecutiveSeparators : UsernameError
    data object OnlyDigits : UsernameError
    data object Reserved : UsernameError
}

fun UsernameValidatorInterface.validateWithResult(
    input: String,
): Result<String, UsernameError> = try {
    Result.Success(validate(input))
} catch (e: UsernameException) {
    Result.Failure(e.toUsernameError())
}

private fun UsernameException.toUsernameError(): UsernameError = when (this) {
    is UsernameException.TooShort -> UsernameError.TooShort(minLen.toInt())
    is UsernameException.TooLong -> UsernameError.TooLong(maxLen.toInt())
    is UsernameException.InvalidCharacter -> UsernameError.InvalidCharacter(character)
    is UsernameException.InvalidStart -> UsernameError.InvalidStart
    is UsernameException.InvalidEnd -> UsernameError.InvalidEnd
    is UsernameException.ConsecutiveSeparators -> UsernameError.ConsecutiveSeparators
    is UsernameException.OnlyDigits -> UsernameError.OnlyDigits
    is UsernameException.Reserved -> UsernameError.Reserved
}
