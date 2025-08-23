package de.davis.keygo.autofill.presentation

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import de.davis.keygo.autofill.presentation.activity.AutofillActivity
import de.davis.keygo.autofill.presentation.model.Extraction
import de.davis.keygo.core.domain.alias.ItemId

private const val AUTOFILL_PENDING_INTENT_FLAGS =
    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

// TODO: show UI
internal fun Context.getSelectionPendingIntent(
    context: Context,
    extraction: Extraction,
    vaultId: ItemId
) =
    PendingIntent.getActivity(
        this,
        1001,
        AutofillActivity.newIntent(context, extraction, vaultId),
        AUTOFILL_PENDING_INTENT_FLAGS
    )

internal fun Context.getOnLongClickPendingIntent() = PendingIntent.getService(
    this,
    0,
    Intent(),
    PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
)