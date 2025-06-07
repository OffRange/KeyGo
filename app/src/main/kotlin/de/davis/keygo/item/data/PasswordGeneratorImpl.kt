package de.davis.keygo.item.data

import de.davis.keygo.item.domain.PasswordGenerator
import de.davis.passgen.configs.digits
import de.davis.passgen.configs.lowercase
import de.davis.passgen.configs.punctuations
import de.davis.passgen.configs.uppercase
import de.davis.passgen.generators.Password
import de.davis.passgen.generators.generate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

@Single
class PasswordGeneratorImpl : PasswordGenerator {

    override suspend fun generatePassword(
        length: Int,
        useUppercase: Boolean,
        useLowercase: Boolean,
        useNumbers: Boolean,
        useSymbols: Boolean
    ): String = withContext(Dispatchers.Default) {
        generate(Password) {
            length(length)

            if (useUppercase) uppercase()
            if (useLowercase) lowercase()
            if (useNumbers) digits()
            if (useSymbols) punctuations()
        }
    }
}