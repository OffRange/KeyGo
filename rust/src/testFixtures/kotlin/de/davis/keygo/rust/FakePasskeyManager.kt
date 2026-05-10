package de.davis.keygo.rust

import de.davisalessandro.keygo.rust.RegistrationResponse
import de.davisalessandro.keygo.rust.RustPasskeyInterface

class FakePasskeyManager : RustPasskeyInterface {

    data class AuthenticateCall(
        val jsonRequest: String,
        val passkey: ByteArray,
        val clientDataHash: ByteArray?,
    )

    val authenticateCalls = mutableListOf<AuthenticateCall>()
    val registerCalls = mutableListOf<String>()
    val excludedCredentialsCalls = mutableListOf<String>()

    /** Result returned by [authenticate]. Throws if null. */
    var authenticateResult: String? = null

    /** Result returned by [register]. Throws if null. */
    var registerResult: RegistrationResponse? = null

    /** Result returned by [excludedCredentials]. */
    var excludedCredentialsResult: List<ByteArray> = emptyList()

    override suspend fun authenticate(
        jsonRequest: String,
        passkey: ByteArray,
        clientDataHash: ByteArray?,
    ): String {
        authenticateCalls += AuthenticateCall(jsonRequest, passkey.copyOf(), clientDataHash?.copyOf())
        return authenticateResult ?: error("authenticateResult not set on FakePasskeyManager")
    }

    override suspend fun excludedCredentials(jsonRequest: String): List<ByteArray> {
        excludedCredentialsCalls += jsonRequest
        return excludedCredentialsResult
    }

    override suspend fun register(jsonRequest: String): RegistrationResponse {
        registerCalls += jsonRequest
        return registerResult ?: error("registerResult not set on FakePasskeyManager")
    }
}
