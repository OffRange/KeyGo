package de.davis.keygo.feature.autofill.presentation

import de.davis.keygo.feature.autofill.presentation.model.FieldFeatures
import de.davis.keygo.feature.autofill.presentation.model.FieldType
import kotlin.test.Test
import kotlin.test.assertEquals

class ClassifierTest {

    // Helper function to reduce boilerplate
    private fun classify(
        autofillHints: Set<String> = emptySet(),
        htmlAttributes: Map<String, String> = emptyMap(),
        tokens: Set<String> = emptySet(),
    ): FieldType = Classifier.classify(FieldFeatures(autofillHints, htmlAttributes, tokens))

    // ====== Group 1: Precedence (autofillHints beat htmlAttributes beat tokens) ======

    @Test
    fun `autofill hints take precedence over html attributes`() {
        // autofillHints = {"password"}, htmlAttributes = {"type":"email"}, tokens = {"username"}
        // → Password (autofillHints win)
        val result = classify(
            autofillHints = setOf("password"),
            htmlAttributes = mapOf("type" to "email"),
            tokens = setOf("username"),
        )
        assertEquals(FieldType.Credentials.Password, result)
    }

    @Test
    fun `html attributes take precedence over tokens`() {
        // autofillHints = {}, htmlAttributes = {"type":"email"}, tokens = {"username"}
        // → EMail (htmlAttributes win over tokens)
        val result = classify(
            autofillHints = emptySet(),
            htmlAttributes = mapOf("type" to "email"),
            tokens = setOf("username"),
        )
        assertEquals(FieldType.Credentials.EMail, result)
    }

    @Test
    fun `tokens are used when no hints or attributes match`() {
        // autofillHints = {}, htmlAttributes = {}, tokens = {"username"}
        // → Username (tokens win over nothing)
        val result = classify(
            autofillHints = emptySet(),
            htmlAttributes = emptyMap(),
            tokens = setOf("username"),
        )
        assertEquals(FieldType.Credentials.Username, result)
    }

    // ====== Group 2: autofillHints exact constants ======
    // These are Android View autofill hint constants

    @Test
    fun `autofill hint username is recognized`() {
        val result = classify(autofillHints = setOf("username"))
        assertEquals(FieldType.Credentials.Username, result)
    }

    @Test
    fun `autofill hint phone is recognized`() {
        val result = classify(autofillHints = setOf("phone"))
        assertEquals(FieldType.Credentials.Phone, result)
    }

    @Test
    fun `autofill hint emailAddress is recognized`() {
        val result = classify(autofillHints = setOf("emailAddress"))
        assertEquals(FieldType.Credentials.EMail, result)
    }

    @Test
    fun `autofill hint password is recognized`() {
        val result = classify(autofillHints = setOf("password"))
        assertEquals(FieldType.Credentials.Password, result)
    }

    @Test
    fun `autofill hint one-time-code is recognized`() {
        val result = classify(autofillHints = setOf("one-time-code"))
        assertEquals(FieldType.TOTP, result)
    }

    // ====== Group 3: htmlAttributes ======

    @Test
    fun `html type password is recognized`() {
        val result = classify(htmlAttributes = mapOf("type" to "password"))
        assertEquals(FieldType.Credentials.Password, result)
    }

    @Test
    fun `html type email is recognized`() {
        val result = classify(htmlAttributes = mapOf("type" to "email"))
        assertEquals(FieldType.Credentials.EMail, result)
    }

    @Test
    fun `html type text with no other keys is undefined`() {
        val result = classify(htmlAttributes = mapOf("type" to "text"))
        assertEquals(FieldType.Undefined, result)
    }

    @Test
    fun `html type text with chromium one-time-code hint is recognized as TOTP`() {
        val result = classify(
            htmlAttributes = mapOf(
                "type" to "text",
                "computed-autofill-hints" to "HTML_TYPE_ONE_TIME_CODE"
            )
        )
        assertEquals(FieldType.TOTP, result)
    }

    @Test
    fun `html type text with chromium username hint is recognized`() {
        val result = classify(
            htmlAttributes = mapOf(
                "type" to "text",
                "computed-autofill-hints" to "USERNAME"
            )
        )
        assertEquals(FieldType.Credentials.Username, result)
    }

    @Test
    fun `html type text with unknown chromium hint is undefined`() {
        val result = classify(
            htmlAttributes = mapOf(
                "type" to "text",
                "computed-autofill-hints" to "UNKNOWN_HINT"
            )
        )
        assertEquals(FieldType.Undefined, result)
    }

    @Test
    fun `html name password fallback is recognized`() {
        val result = classify(htmlAttributes = mapOf("type" to "text", "name" to "password"))
        assertEquals(FieldType.Credentials.Password, result)
    }

    @Test
    fun `html name email fallback is recognized`() {
        val result = classify(htmlAttributes = mapOf("type" to "text", "name" to "email"))
        assertEquals(FieldType.Credentials.EMail, result)
    }

    @Test
    fun `html name random field fallback is undefined`() {
        val result = classify(htmlAttributes = mapOf("type" to "text", "name" to "randomfield"))
        assertEquals(FieldType.Undefined, result)
    }

    // ====== Group 4: USERNAME_REGEX (tokens, case-insensitive, word-boundary lookarounds) ======

    @Test
    fun `token username is recognized`() {
        val result = classify(tokens = setOf("username"))
        assertEquals(FieldType.Credentials.Username, result)
    }

    @Test
    fun `token login is recognized`() {
        val result = classify(tokens = setOf("login"))
        assertEquals(FieldType.Credentials.Username, result)
    }

    @Test
    fun `token user is recognized`() {
        val result = classify(tokens = setOf("user"))
        assertEquals(FieldType.Credentials.Username, result)
    }

    @Test
    fun `token usuario is recognized`() {
        val result = classify(tokens = setOf("usuario"))
        assertEquals(FieldType.Credentials.Username, result)
    }

    @Test
    fun `token benutzername is recognized`() {
        val result = classify(tokens = setOf("benutzername"))
        assertEquals(FieldType.Credentials.Username, result)
    }

    @Test
    fun `token with alphanumeric prefix is not recognized as username`() {
        // Prefix alphanumeric prevents match
        val result = classify(tokens = setOf("ausername"))
        assertEquals(FieldType.Undefined, result)
    }

    @Test
    fun `token with alphanumeric suffix is not recognized as username`() {
        // Suffix alphanumeric prevents match
        val result = classify(tokens = setOf("username1"))
        assertEquals(FieldType.Undefined, result)
    }

    // ====== Group 5: EMAIL_REGEX (tokens) ======

    @Test
    fun `token email is recognized`() {
        val result = classify(tokens = setOf("email"))
        assertEquals(FieldType.Credentials.EMail, result)
    }

    @Test
    fun `token account is recognized`() {
        val result = classify(tokens = setOf("account"))
        assertEquals(FieldType.Credentials.EMail, result)
    }

    @Test
    fun `token correo is recognized`() {
        val result = classify(tokens = setOf("correo"))
        assertEquals(FieldType.Credentials.EMail, result)
    }

    @Test
    fun `token konto is recognized`() {
        val result = classify(tokens = setOf("konto"))
        assertEquals(FieldType.Credentials.EMail, result)
    }

    @Test
    fun `token emailaddress is not recognized due to boundary`() {
        // "email" is followed by "a" (alphanumeric) — boundary check fails
        val result = classify(tokens = setOf("emailaddress"))
        assertEquals(FieldType.Undefined, result)
    }

    // ====== Group 6: TOTP_REGEX (tokens) ======

    @Test
    fun `token totp is recognized`() {
        val result = classify(tokens = setOf("totp"))
        assertEquals(FieldType.TOTP, result)
    }

    @Test
    fun `token 2fa is recognized`() {
        val result = classify(tokens = setOf("2fa"))
        assertEquals(FieldType.TOTP, result)
    }

    @Test
    fun `token mfa is recognized`() {
        val result = classify(tokens = setOf("mfa"))
        assertEquals(FieldType.TOTP, result)
    }

    @Test
    fun `token two-factor authentication is recognized`() {
        val result = classify(tokens = setOf("two-factor authentication"))
        assertEquals(FieldType.TOTP, result)
    }

    @Test
    fun `token authenticator app code is recognized`() {
        val result = classify(tokens = setOf("authenticator app code"))
        assertEquals(FieldType.TOTP, result)
    }

    @Test
    fun `token otp code is recognized`() {
        val result = classify(tokens = setOf("otp code"))
        assertEquals(FieldType.TOTP, result)
    }

    @Test
    fun `token sms code is not recognized as TOTP`() {
        // SMS exclusion applies
        val result = classify(tokens = setOf("sms code"))
        assertEquals(FieldType.Undefined, result)
    }

    @Test
    fun `token email verification code is recognized as email`() {
        // "email verification code" contains "email", which matches EMAIL_REGEX
        // (the TOTP_REGEX exclusion for "email" only applies at TOTP pattern level, not EMAIL_REGEX level)
        val result = classify(tokens = setOf("email verification code"))
        assertEquals(FieldType.Credentials.EMail, result)
    }

    @Test
    fun `token phone verification code is not recognized`() {
        // Phone exclusion applies
        val result = classify(tokens = setOf("phone verification code"))
        assertEquals(FieldType.Undefined, result)
    }

    @Test
    fun `token backup codes is not recognized`() {
        // Backup codes exclusion applies
        val result = classify(tokens = setOf("backup codes"))
        assertEquals(FieldType.Undefined, result)
    }

    @Test
    fun `token recovery codes is not recognized`() {
        // Recovery codes exclusion applies
        val result = classify(tokens = setOf("recovery codes"))
        assertEquals(FieldType.Undefined, result)
    }

    // ====== Group 7: All-undefined / edge cases ======

    @Test
    fun `all empty inputs are undefined`() {
        val result = classify()
        assertEquals(FieldType.Undefined, result)
    }

    @Test
    fun `blank autofill hint is undefined`() {
        val result = classify(autofillHints = setOf(""))
        assertEquals(FieldType.Undefined, result)
    }

    @Test
    fun `multiple non-matching tokens are undefined`() {
        val result = classify(tokens = setOf("foo", "bar", "baz"))
        assertEquals(FieldType.Undefined, result)
    }

    @Test
    fun `token matching is case-insensitive`() {
        // Tokens are matched case-insensitively via USERNAME_REGEX
        val result = classify(tokens = setOf("USERNAME"))
        assertEquals(FieldType.Credentials.Username, result)
    }

    @Test
    fun `with multiple autofill hints the first non-undefined wins`() {
        // classifyTokens iterates and returns first non-Undefined
        // setOf() preserves insertion order, so "username" is first
        val result = classify(autofillHints = setOf("username", "password"))
        assertEquals(FieldType.Credentials.Username, result)
    }

    @Test
    fun `with multiple matching tokens the first match wins`() {
        // If multiple tokens match, returns first one encountered
        // setOf() preserves insertion order, so "username" is first
        val result = classify(tokens = setOf("username", "email"))
        assertEquals(FieldType.Credentials.Username, result)
    }

    @Test
    fun `html type matching is case-sensitive`() {
        // HTML type matching is case-sensitive in the when() block, so "PASSWORD" won't match
        val result = classify(htmlAttributes = mapOf("type" to "PASSWORD"))
        assertEquals(FieldType.Undefined, result)
    }

    @Test
    fun `html attributes missing type return undefined`() {
        val result = classify(htmlAttributes = mapOf("placeholder" to "Enter username"))
        assertEquals(FieldType.Undefined, result)
    }

    @Test
    fun `empty html attributes map is undefined`() {
        val result = classify(htmlAttributes = emptyMap())
        assertEquals(FieldType.Undefined, result)
    }

    @Test
    fun `empty tokens set is undefined`() {
        val result = classify(tokens = emptySet())
        assertEquals(FieldType.Undefined, result)
    }
}
