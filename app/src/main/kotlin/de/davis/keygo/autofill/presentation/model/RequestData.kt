package de.davis.keygo.autofill.presentation.model

import android.os.Parcelable
import de.davis.keygo.core.item.domain.alias.ItemId
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
sealed interface RequestData : Parcelable {
    val form: Form
    val requestId: Int
}

data class SaveRequestData(
    override val form: Form,
) : RequestData {

    @IgnoredOnParcel
    override val requestId: Int = 2001
}

@Parcelize
sealed interface FillRequestData : RequestData {

    data class Suggestion(
        override val form: Form,
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
        override val form: Form,
    ) : FillRequestData {
        @IgnoredOnParcel
        override val requestId: Int = 1002
    }

    data class Pinned(
        override val form: Form,
    ) : FillRequestData {
        @IgnoredOnParcel
        override val requestId: Int = 1001
    }
}

fun pinnedRequestData(formInformation: Form) = FillRequestData.Pinned(form = formInformation)

fun appRequestData(formInformation: Form) = FillRequestData.App(form = formInformation)

fun suggestionRequestData(formInformation: Form, vaultId: ItemId, index: Int) =
    FillRequestData.Suggestion(
        form = formInformation,
        vaultId = vaultId,
        index = index
    )