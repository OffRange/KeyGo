package de.davis.keygo.autofill.presentation

import android.content.Context
import de.davis.keygo.R
import de.davis.keygo.autofill.presentation.model.FormType
import de.davis.keygo.core.item.domain.model.lite.LitePassword
import de.davis.keygo.core.item.domain.model.lite.LiteVaultItem

internal fun LiteVaultItem.subtitle(context: Context, formType: FormType) = when (formType) {
    is FormType.TOTP -> context.getString(R.string.totp)
    else -> when (this) {
        is LitePassword -> username ?: "----"
    }
}