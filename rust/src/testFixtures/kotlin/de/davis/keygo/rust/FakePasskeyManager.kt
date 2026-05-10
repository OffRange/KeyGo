package de.davis.keygo.rust

import de.davisalessandro.keygo.rust.RegistrationResponse
import de.davisalessandro.keygo.rust.RustPasskeyInterface

class FakePasskeyManager : RustPasskeyInterface {

    override suspend fun authenticate(
        jsonRequest: String,
        passkey: ByteArray,
        clientDataHash: ByteArray?,
    ): String = error("not implemented in fake")

    override suspend fun excludedCredentials(jsonRequest: String): List<ByteArray> = emptyList()

    override suspend fun register(jsonRequest: String): RegistrationResponse =
        error("not implemented in fake")
}
