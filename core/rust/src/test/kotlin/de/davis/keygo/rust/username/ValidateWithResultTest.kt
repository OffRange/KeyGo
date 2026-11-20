package de.davis.keygo.rust.username

import de.davis.keygo.core.util.Result
import de.davis.keygo.rust.FakeUsernameValidator
import de.davisalessandro.keygo.rust.UsernameException
import kotlin.test.Test
import kotlin.test.assertEquals

class ValidateWithResultTest {

    private val validator = FakeUsernameValidator()

    @Test
    fun returns_the_normalized_name() {
        assertEquals(Result.Success("ada"), validator.validateWithResult("Ada"))
    }

    @Test
    fun maps_every_exception_to_its_error() {
        val cases = mapOf(
            UsernameException.TooShort(3u) to UsernameError.TooShort(3),
            UsernameException.TooLong(32u) to UsernameError.TooLong(32),
            UsernameException.InvalidCharacter("ö") to UsernameError.InvalidCharacter("ö"),
            UsernameException.InvalidStart() to UsernameError.InvalidStart,
            UsernameException.InvalidEnd() to UsernameError.InvalidEnd,
            UsernameException.ConsecutiveSeparators() to UsernameError.ConsecutiveSeparators,
            UsernameException.OnlyDigits() to UsernameError.OnlyDigits,
            UsernameException.Reserved() to UsernameError.Reserved,
        )

        for ((exception, error) in cases) {
            validator.errors = mapOf("x" to exception)
            assertEquals(Result.Failure(error), validator.validateWithResult("x"))
        }
    }
}
