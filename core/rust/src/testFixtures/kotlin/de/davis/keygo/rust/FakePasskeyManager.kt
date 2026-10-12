package de.davis.keygo.rust

import de.davisalessandro.keygo.rust.CreationInfo
import de.davisalessandro.keygo.rust.Registration
import de.davisalessandro.keygo.rust.RustPasskeyInterface

class FakePasskeyManager : RustPasskeyInterface {

    data class AuthenticateCall(
        val jsonRequest: String,
        val passkey: ByteArray,
        val clientDataHash: ByteArray?,
    )

    val authenticateCalls = mutableListOf<AuthenticateCall>()
    val registerCalls = mutableListOf<String>()
    val passkeyInformationCalls = mutableListOf<String>()

    /** Result returned by [authenticate]. Throws if null. */
    var authenticateResult: String? = null

    /** Result returned by [register]. Throws if null. */
    var registerResult: Registration? = null

    /** Result returned by [passkeyInformation]. */
    var passkeyInformationResult: CreationInfo = CreationInfo(
        excludeCredentials = emptyList(),
        rp = "example.com",
    )

    override suspend fun authenticate(
        jsonRequest: String,
        passkey: ByteArray,
        clientDataHash: ByteArray?,
    ): String {
        authenticateCalls += AuthenticateCall(jsonRequest, passkey.copyOf(), clientDataHash?.copyOf())
        return authenticateResult ?: error("authenticateResult not set on FakePasskeyManager")
    }

    override fun passkeyInformation(jsonRequest: String): CreationInfo {
        passkeyInformationCalls += jsonRequest
        return passkeyInformationResult
    }

    override suspend fun register(jsonRequest: String): Registration {
        registerCalls += jsonRequest
        return registerResult ?: error("registerResult not set on FakePasskeyManager")
    }
}
