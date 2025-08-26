package de.davis.keygo.autofill.presentation.model

import android.os.Parcelable
import de.davis.keygo.core.domain.alias.ItemId
import kotlinx.parcelize.Parcelize

@Parcelize
sealed interface AutofillInformation : Parcelable {

    val extraction: Extraction

    data class Suggestion(
        override val extraction: Extraction,
        val vaultId: ItemId,
    ) : AutofillInformation

    data class App(
        override val extraction: Extraction,
    ) : AutofillInformation

    companion object {
        fun from(intentData: AutofillIntentData): AutofillInformation = when (intentData) {
            is AutofillIntentData.Suggestion -> Suggestion(
                extraction = intentData.extraction,
                vaultId = intentData.vaultId
            )

            is AutofillIntentData.App -> App(
                extraction = intentData.extraction
            )

            is AutofillIntentData.Pinned -> App(
                extraction = intentData.extraction
            )
        }
    }
}
