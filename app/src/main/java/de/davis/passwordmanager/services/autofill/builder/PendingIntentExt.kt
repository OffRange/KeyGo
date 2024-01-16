package de.davis.passwordmanager.services.autofill.builder

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import de.davis.passwordmanager.services.autofill.SelectionActivity
import de.davis.passwordmanager.services.autofill.entities.AutofillForm


private const val AUTOFILL_PENDING_INTENT_FLAGS =
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_UPDATE_CURRENT

@RequiresApi(Build.VERSION_CODES.O)
fun Context.getOpenAppPendingIntent(
    requestCode: Int,
    autofillForm: AutofillForm
): PendingIntent =
    PendingIntent.getActivity(
        this,
        requestCode,
        SelectionActivity.newIntent(this, autofillForm),
        AUTOFILL_PENDING_INTENT_FLAGS
    )

internal fun Context.getOnLongClickPendingIntent() = PendingIntent.getService(
    this,
    0,
    Intent(),
    AUTOFILL_PENDING_INTENT_FLAGS
)