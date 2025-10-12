package de.davis.keygo.autofill.presentation

import android.view.View
import de.davis.keygo.autofill.presentation.model.FieldFeatures
import de.davis.keygo.autofill.presentation.model.FieldType

internal object Classifier {

    fun classify(features: FieldFeatures): FieldType {
        var type = classifyTokens(features.autofillHints)
        if (type !is FieldType.Undefined) return type

        type = classifyHtmlAttributes(features.htmlAttributes)
        if (type !is FieldType.Undefined) return type

        //TODO: handle input types

        type = classifyTokens(features.tokens)
        if (type !is FieldType.Undefined) return type

        return type
    }

    private fun classifyHtmlAttributes(htmlAttributes: Map<String, String>): FieldType {
        val type = htmlAttributes["type"]
        if (type == null) return FieldType.Undefined

        when (type) {
            "password" -> return FieldType.Credentials.Password
            "email" -> return FieldType.Credentials.EMail
        }

        // Chromium only
        htmlAttributes["computed-autofill-hints"]?.let { computedAutofillHints ->
            CHROMIUM_AUTOFILL_HINTS[computedAutofillHints]?.let {
                return it
            }
        }

        return htmlAttributes["name"]?.let {
            classifyToken(it)
        } ?: FieldType.Undefined
    }

    private fun classifyTokens(tokens: Set<String>): FieldType {
        tokens.forEach {
            when (val type = classifyToken(it)) {
                FieldType.Undefined -> {}
                else -> return type
            }
        }

        return FieldType.Undefined
    }

    private fun classifyToken(token: String): FieldType {
        val type = when (token) {
            View.AUTOFILL_HINT_USERNAME -> FieldType.Credentials.Username
            View.AUTOFILL_HINT_PHONE -> FieldType.Credentials.Phone
            View.AUTOFILL_HINT_EMAIL_ADDRESS -> FieldType.Credentials.EMail

            View.AUTOFILL_HINT_PASSWORD -> FieldType.Credentials.Password

            // Token for the autocomplete parameter for html form inputs, see: http://is.gd/whatwg_autocomplete
            // or https://html.spec.whatwg.org/multipage/form-control-infrastructure.html#autofilling-form-controls:-the-autocomplete-attribute
            "one-time-code" -> FieldType.TOTP

            else -> FieldType.Undefined
        }

        if (type !is FieldType.Undefined)
            return type

        if (USERNAME_REGEX.containsMatchIn(token)) return FieldType.Credentials.Username
        if (EMAIL_REGEX.containsMatchIn(token)) return FieldType.Credentials.EMail
        if (TOTP_REGEX.containsMatchIn(token)) return FieldType.TOTP

        return FieldType.Undefined
    }

    private val TOTP_REGEX = Regex(
        pattern = """
        (?xi)                                                                           # ignore case; allow comments/whitespace
        ^(?!.*\b(?:sms|text(?:\s?message)?|email|e-?mail|mail|phone|call|voice)\b)      # not SMS/email/phone
         (?!.*\b(?:backup|recovery)\s+codes?\b)                                         # not backup/recovery codes
        .*?
        (?:                                                                             # signals of a TOTP-style field
            (?<![A-Z0-9])totp(?![A-Z0-9])
          | (?<![A-Z0-9])(?:2fa|mfa)(?![A-Z0-9])
          | two[-_\s]?factor(?:\s+(?:auth(?:entication|enticator)?|verification))?(?:\s+(?:code|token|passcode|password))?
          | authenticator(?:\s*app)?(?:\s+(?:code|token|passcode|verification\s+code))?
          | (?<![A-Z0-9])(?:one[-_\s]?time|otp)(?![A-Z0-9])(?:\s+(?:password|passcode|code))?
          | (?<![A-Z0-9])[68](?![A-Z0-9])\s*[-_\s]?\s*digit\s+(?:code|token|passcode)\b(?=.*\b(?:app|authenticator|2fa|two[-_\s]?factor|mfa)\b)
          | (?<![A-Z0-9])(?:verification|login|security)\s+code(?![A-Z0-9])(?=.*\b(?:authenticator|2fa|two[-_\s]?factor|mfa|app)\b)
        )
        """.trimIndent(),
        setOf(RegexOption.IGNORE_CASE, RegexOption.COMMENTS)
    )

    // TODO: use more sophisticated regexes
    private val USERNAME_REGEX = Regex(
        "\\b(username|login|user|usuario|nombredeusuario|nomd'utilisateur|" +
                "benutzername|nomeusuario|nomeutente|imyapolzovatelya|yonghuming|" +
                "yūzāmei|ismalmustakhdim)" +
                "(\\b|$)", RegexOption.IGNORE_CASE
    )

    private val EMAIL_REGEX = Regex(
        "\\b(email|account|correo|cuenta|adressemail|compte|" +
                "emailadresse|konto|conta|uchetnayazapis|elektronnayapochta|" +
                "zhanghu|dianziyoujian|akaunto|mēruadoresu|alhisab|albaridal(')?iliktruni)" +
                "(\\b|$)", RegexOption.IGNORE_CASE
    )
}