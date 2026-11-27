package de.davis.keygo.rust

import de.davisalessandro.keygo.rust.UsernameException
import de.davisalessandro.keygo.rust.UsernameValidatorInterface

class FakeUsernameValidator : UsernameValidatorInterface {

    var validateResult: (String) -> String = { it.lowercase() }
    var sanitizeResult: (String) -> String = { input ->
        input.lowercase().filter { it in 'a'..'z' || it in '0'..'9' || it in "._-" }
    }
    var minLenResult: UInt = 3u
    var maxLenResult: UInt = 32u
    var allowedCharsResult: List<String> = listOf("a-z", "0-9", ".", "_", "-")

    /** Inputs that fail validation, mapped to the error they raise. */
    var errors: Map<String, UsernameException> = emptyMap()

    override fun validate(input: String): String {
        errors[input]?.let { throw it }
        return validateResult(input)
    }

    override fun sanitize(input: String): String = sanitizeResult(input)

    override fun minLen(): UInt = minLenResult

    override fun maxLen(): UInt = maxLenResult

    override fun allowedChars(): List<String> = allowedCharsResult
}
