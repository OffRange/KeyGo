package de.davis.keygo.feature.autofill.presentation

import android.content.Context
import de.davis.keygo.core.item.domain.model.lite.LiteLogin
import de.davis.keygo.core.item.domain.model.lite.LiteVaultItem
import de.davis.keygo.feature.autofill.presentation.model.FormType
import de.davis.keygo.feature.item.core.R as ItemCoreR

internal fun LiteVaultItem.subtitle(context: Context, formType: FormType) = when (formType) {
    is FormType.TOTP -> context.getString(ItemCoreR.string.totp)
    else -> when (this) {
        is LiteLogin -> username ?: "----"
    }
}