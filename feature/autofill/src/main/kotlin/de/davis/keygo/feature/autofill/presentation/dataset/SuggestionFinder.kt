package de.davis.keygo.feature.autofill.presentation.dataset

import de.davis.keygo.core.item.domain.model.lite.LiteLogin
import de.davis.keygo.core.item.domain.model.lite.LiteVaultItem
import de.davis.keygo.core.security.domain.usecase.GetTdlMatchedLoginsUseCase
import de.davis.keygo.feature.autofill.presentation.model.FieldType
import de.davis.keygo.feature.autofill.presentation.model.Form
import de.davis.keygo.feature.autofill.presentation.model.FormType
import org.koin.core.annotation.Single

@Single
internal class SuggestionFinder(
    private val getTdlMatchedLogins: GetTdlMatchedLoginsUseCase,
) {

    internal suspend fun findVaultSuggestions(
        form: Form,
        count: Int,
    ): List<LiteVaultItem> {
        if (count == 0) return emptyList()

        return when (form.type) {
            is FormType.Credentials -> findLoginSuggestions(
                form,
                count,
                withPassword = form.requiresPassword(),
                withUsername = form.requiresUsername()
            )

            is FormType.TOTP -> findLoginSuggestions(form, count, withTOTP = true)
        }
    }

    private suspend fun findLoginSuggestions(
        form: Form,
        count: Int,
        withPassword: Boolean = false,
        withUsername: Boolean = false,
        withTOTP: Boolean = false,
    ): List<LiteLogin> = form.url?.let {
        getTdlMatchedLogins(
            it,
            requireTotp = withTOTP,
            requirePassword = withPassword,
            requireUsername = withUsername,
            limit = count,
        )
    } ?: emptyList()

    private fun Form.credentialFields() = fields.filter { it.type is FieldType.Credentials }

    private fun Form.requiresPassword(): Boolean {
        val creds = credentialFields()
        return creds.isNotEmpty() && creds.all { it.type is FieldType.Credentials.Password }
    }

    private fun Form.requiresUsername(): Boolean {
        val creds = credentialFields()
        // none because FieldType.Credentials has three non-password subtypes. All these subtypes
        // map to the username column in the database. So an email-only or phone-only form should
        // equally filter for logins that have a username set.
        return creds.isNotEmpty() && creds.none { it.type is FieldType.Credentials.Password }
    }
}
