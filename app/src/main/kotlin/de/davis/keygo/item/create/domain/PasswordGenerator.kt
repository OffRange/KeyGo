package de.davis.keygo.item.create.domain

interface PasswordGenerator {

    suspend fun generatePassword(
        length: Int,
        useUppercase: Boolean = true,
        useLowercase: Boolean = true,
        useNumbers: Boolean = true,
        useSymbols: Boolean = true,
    ): String
}