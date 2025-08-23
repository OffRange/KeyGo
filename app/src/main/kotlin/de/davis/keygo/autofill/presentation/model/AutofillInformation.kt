package de.davis.keygo.autofill.presentation.model

import android.os.Parcelable
import de.davis.keygo.core.domain.alias.ItemId
import kotlinx.parcelize.Parcelize

@Parcelize
data class AutofillInformation(
    val extraction: Extraction,
    val vaultId: ItemId
) : Parcelable
