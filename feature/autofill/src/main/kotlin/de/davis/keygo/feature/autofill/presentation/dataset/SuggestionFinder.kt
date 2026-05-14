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
            is FormType.Credentials -> findLoginSuggestions(form, count)
            is FormType.TOTP -> findLoginSuggestions(form, count, withTOTP = true)
        }
    }

    private suspend fun findLoginSuggestions(
        form: Form,
        count: Int,
        withTOTP: Boolean = false,
    ): List<LiteLogin> = form.url?.let {
        getTdlMatchedLogins(
            it,
            requireTotp = withTOTP,
            requirePassword = form.requiresPassword(),
            limit = count,
        )
    } ?: emptyList()

    private fun Form.requiresPassword(): Boolean {
        val hasPasswordField = fields.any { it.type is FieldType.Credentials.Password }
        val hasUsernameField = fields.any {
            it.type is FieldType.Credentials.Username
                || it.type is FieldType.Credentials.EMail
                || it.type is FieldType.Credentials.Phone
        }
        return hasPasswordField && !hasUsernameField
    }
}
