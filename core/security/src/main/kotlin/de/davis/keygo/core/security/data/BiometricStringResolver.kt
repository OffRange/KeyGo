package de.davis.keygo.core.security.data

import android.content.Context
import de.davis.keygo.core.security.R
import de.davis.keygo.core.security.domain.model.BiometricString

internal fun BiometricString.resolve(context: Context) = when (this) {
    is BiometricString.Title.Authenticate -> context.getString(
        R.string.authenticate
    )

    is BiometricString.Title.UnlockItem -> context.getString(
        R.string.unlock_item,
        itemName
    )

    is BiometricString.NegativeButton.Cancel -> context.getString(
        R.string.cancel
    )

    is BiometricString.NegativeButton.Password -> context.getString(
        R.string.use_password
    )
}