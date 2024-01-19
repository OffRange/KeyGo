package de.davis.passwordmanager.services.autofill.entities

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SaveForm(
    val identifierSaveField: SaveField? = null,
    val passwordSaveField: SaveField? = null,
    val url: String? = null
) : Parcelable