package de.davis.keygo.core.item.domain.model

import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.domain.alias.newVaultId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LoginTest {

    @Test
    fun `hasAnyContent is false for empty login`() {
        val login = baseLogin()
        assertFalse(login.hasAnyContent)
    }

    @Test
    fun `hasAnyContent is true when username is non-blank`() {
        val login = baseLogin(username = "alice")
        assertTrue(login.hasAnyContent)
    }

    @Test
    fun `hasAnyContent is false when username is blank`() {
        val login = baseLogin(username = "   ")
        assertFalse(login.hasAnyContent)
    }

    @Test
    fun `hasAnyContent is true when passwordCredential is set`() {
        val login = baseLogin(
            passwordCredential = PasswordCredential(
                secret = PasswordSecret(EncryptedPayload.EMPTY),
                score = PasswordScore.Strong,
            ),
        )
        assertTrue(login.hasAnyContent)
    }

    @Test
    fun `hasAnyContent is true when totp is set`() {
        val login = baseLogin(
            totp = Totp(
                loginId = newItemId(),
                secret = Totp.Secret(EncryptedPayload.EMPTY),
                accountName = "alice",
                issuer = "example",
            ),
        )
        assertTrue(login.hasAnyContent)
    }

    @Test
    fun `hasAnyContent is true when passkeyRPs is non-empty`() {
        val login = baseLogin(passkeyRPs = setOf("example.com"))
        assertTrue(login.hasAnyContent)
    }

    private fun baseLogin(
        username: String? = null,
        passwordCredential: PasswordCredential? = null,
        totp: Totp? = null,
        passkeyRPs: Set<String> = emptySet(),
    ): Login = Login(
        id = newItemId(),
        username = username,
        domainInfos = emptySet(),
        passwordCredential = passwordCredential,
        totp = totp,
        passkeyRPs = passkeyRPs,
        vaultId = newVaultId(),
        name = "Test",
        keyInformation = KeyInformation(byteArrayOf(), byteArrayOf()),
        note = null,
        pinned = false,
        timestamp = Timestamp(),
    )
}
