package de.davis.keygo.autofill.domain.usecase

import android.view.View
import de.davis.keygo.autofill.domain.model.FieldFeatures
import de.davis.keygo.autofill.domain.model.FieldType
import org.koin.core.annotation.Single

@Single
internal class ClassificationUseCase {

    operator fun invoke(features: FieldFeatures): FieldType {
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
        // TODO: handle computed-autofill-hints (only Chromium) --> components/autofill/core/browser/field_types.h
        val type = htmlAttributes["type"]
        if (type == null) return FieldType.Undefined

        when (type) {
            "password" -> return FieldType.Password
            "email" -> return FieldType.Identifier.EMail
        }

        return FieldType.Undefined
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
            View.AUTOFILL_HINT_USERNAME -> FieldType.Identifier.Username
            View.AUTOFILL_HINT_PHONE -> FieldType.Identifier.Phone
            View.AUTOFILL_HINT_EMAIL_ADDRESS -> FieldType.Identifier.EMail

            View.AUTOFILL_HINT_PASSWORD -> FieldType.Password

            else -> FieldType.Undefined
        }

        if (type !is FieldType.Undefined)
            return type

        if (USERNAME_REGEX.containsMatchIn(token)) return FieldType.Identifier.Username
        if (EMAIL_REGEX.containsMatchIn(token)) return FieldType.Identifier.EMail

        return FieldType.Undefined
    }

    companion object {
        // TODO: use more sophisticated regexes
        val USERNAME_REGEX = Regex(
            "\\b(username|login|user|usuario|nombredeusuario|nomd'utilisateur|" +
                    "benutzername|nomeusuario|nomeutente|imyapolzovatelya|yonghuming|" +
                    "yūzāmei|ismalmustakhdim)" +
                    "(\\b|$)", RegexOption.IGNORE_CASE
        )

        val EMAIL_REGEX = Regex(
            "\\b(email|account|correo|cuenta|adressemail|compte|" +
                    "emailadresse|konto|conta|uchetnayazapis|elektronnayapochta|" +
                    "zhanghu|dianziyoujian|akaunto|mēruadoresu|alhisab|albaridal(')?iliktruni)" +
                    "(\\b|$)", RegexOption.IGNORE_CASE
        )
    }
}