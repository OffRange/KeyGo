package de.davis.passwordmanager.services.autofill.extensions

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import de.davis.passwordmanager.database.dtos.SecureElement
import de.davis.passwordmanager.services.autofill.SaveActivity
import de.davis.passwordmanager.services.autofill.SelectionActivity
import de.davis.passwordmanager.services.autofill.entities.AutofillForm


private const val AUTOFILL_PENDING_INTENT_FLAGS =
    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_UPDATE_CURRENT

@RequiresApi(Build.VERSION_CODES.O)
fun Context.getSelectionPendingIntent(
    requestCode: Int,
    autofillForm: AutofillForm,
    secureElement: SecureElement? = null
): PendingIntent =
    PendingIntent.getActivity(
        this,
        requestCode,
        SelectionActivity.newIntent(this, autofillForm, secureElement),
        AUTOFILL_PENDING_INTENT_FLAGS
    )

fun Context.getSavePendingIntent(
    requestCode: Int,
    element: SecureElement
): PendingIntent =
    PendingIntent.getActivity(
        this,
        requestCode,
        SaveActivity.newIntent(this, element),
        AUTOFILL_PENDING_INTENT_FLAGS
    )

internal fun Context.getOnLongClickPendingIntent() = PendingIntent.getService(
    this,
    0,
    Intent(),
    PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
)