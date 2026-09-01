package de.davis.keygo.feature.autofill.presentation.model

import androidx.navigation3.runtime.NavKey
import de.davis.keygo.feature.item.core.presentation.model.DetailPaneInformation
import de.davis.keygo.feature.list_screen.presentation.ItemListRoute

internal sealed interface Request<T : NavKey> {
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
