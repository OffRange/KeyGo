package de.davis.keygo.autofill.presentation.model

import android.os.Parcelable
import de.davis.keygo.core.domain.alias.ItemId
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
sealed interface RequestData : Parcelable

data class SaveRequestData(
    val passwordField: SaveInfoField,
    val usernameField: SaveInfoField?,
    val emailField: SaveInfoField?,
) : RequestData

@Parcelize
sealed interface FillRequestData : RequestData {

    val extraction: Extraction

    val requestId: Int

    data class Suggestion(
        override val extraction: Extraction,
        val vaultId: ItemId,
        val index: Int
    ) : FillRequestData {

        // We use the index as an offset to ensure unique requestIds for each suggestion. Otherwise
        // the pending intent would be overridden by the last one created, causing only that one to
        // be received.
        @IgnoredOnParcel
        override val requestId: Int = 1003 + index
    }

    data class App(
        override val extraction: Extraction,
    ) : FillRequestData {
        @IgnoredOnParcel
        override val requestId: Int = 1002
    }

    data class Pinned(
        override val extraction: Extraction,
    ) : FillRequestData {
        @IgnoredOnParcel
        override val requestId: Int = 1001
    }
}

fun pinnedRequestData(extraction: Extraction) = FillRequestData.Pinned(extraction = extraction)

fun appRequestData(extraction: Extraction) = FillRequestData.App(extraction = extraction)

fun suggestionRequestData(extraction: Extraction, vaultId: ItemId, index: Int) =
    FillRequestData.Suggestion(
        extraction = extraction,
        vaultId = vaultId,
        index = index
    )