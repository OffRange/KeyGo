package de.davis.keygo.autofill.presentation

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import de.davis.keygo.autofill.presentation.activity.AutofillActivity
import de.davis.keygo.autofill.presentation.model.FillRequestData
import de.davis.keygo.autofill.presentation.model.SaveRequestData

private const val AUTOFILL_PENDING_INTENT_FLAGS =
    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_CANCEL_CURRENT

internal fun Context.getSelectionPendingIntent(
    fillRequestData: FillRequestData
) = PendingIntent.getActivity(
    this,
    fillRequestData.requestId,
    AutofillActivity.newIntent(this, fillRequestData),
    AUTOFILL_PENDING_INTENT_FLAGS
)

internal fun Context.getOnLongClickPendingIntent() = PendingIntent.getService(
    this,
    0,
    Intent(),
    PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
)

internal fun Context.getSavePendingIntent(saveRequestData: SaveRequestData) =
    PendingIntent.getActivity(
        this,
        saveRequestData.requestId,
        AutofillActivity.newIntent(this, saveRequestData),
        AUTOFILL_PENDING_INTENT_FLAGS
    )