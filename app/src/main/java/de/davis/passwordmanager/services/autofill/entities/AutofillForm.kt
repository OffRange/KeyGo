package de.davis.passwordmanager.services.autofill.entities

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@JvmInline
@Parcelize
value class AutofillForm(val autofillFields: MutableList<AutofillField> = mutableListOf()) :
    Parcelable