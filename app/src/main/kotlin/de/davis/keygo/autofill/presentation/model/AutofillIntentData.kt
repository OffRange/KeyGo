package de.davis.keygo.autofill.presentation.model

import android.os.Parcelable
import de.davis.keygo.core.domain.alias.ItemId
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
sealed interface AutofillIntentData : Parcelable {

    val extraction: Extraction

    val requestId: Int

    data class Suggestion(
        override val extraction: Extraction,
        val vaultId: ItemId,
        val index: Int
    ) : AutofillIntentData {

        // We use the index as an offset to ensure unique requestIds for each suggestion. Otherwise
        // the pending intent would be overridden by the last one created, causing only that one to
        // be received.
        @IgnoredOnParcel
        override val requestId: Int = 1003 + index
    }

    data class App(
        override val extraction: Extraction,
    ) : AutofillIntentData {
        @IgnoredOnParcel
        override val requestId: Int = 1002
    }

    data class Pinned(
        override val extraction: Extraction,
    ) : AutofillIntentData {
        @IgnoredOnParcel
        override val requestId: Int = 1001
    }
}

fun pinnedIntentData(extraction: Extraction) =
    AutofillIntentData.Pinned(extraction = extraction)

fun appIntentData(extraction: Extraction) =
    AutofillIntentData.App(extraction = extraction)

fun suggestionIntentData(extraction: Extraction, vaultId: ItemId, index: Int) =
    AutofillIntentData.Suggestion(
        extraction = extraction,
        vaultId = vaultId,
        index = index
    )