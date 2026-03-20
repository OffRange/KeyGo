package de.davis.keygo.feature.autofill.presentation.model

import de.davis.keygo.feature.autofill.presentation.activity.ItemListRoute
import de.davis.keygo.feature.item.core.presentation.model.DetailPaneInformation

internal sealed interface Request<T : Any> {
    val destination: T

    data object SelectItem : Request<ItemListRoute> {
        override val destination: ItemListRoute = ItemListRoute
    }

    data class SaveItem(val createRaw: DetailPaneInformation.CreateRaw) :
        Request<SaveItemDestination> {
        override val destination: SaveItemDestination
            get() = SaveItemDestination(createRaw)
    }

    data object JustAuthenticateWithPwd : Request<Nothing> {
        override val destination: Nothing
            get() = throw NotImplementedError("This should never be called")
    }

    data object None : Request<Nothing> {
        override val destination: Nothing
            get() = throw NotImplementedError("This should never be called")
    }
}