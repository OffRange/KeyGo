package de.davis.keygo.autofill.presentation.model

import de.davis.keygo.core.presentation.model.RouteDestination
import de.davis.keygo.item.core.presentation.model.DetailPaneInformation

sealed interface Request<T : Any> {
    val destination: T

    data object SelectItem : Request<RouteDestination> {
        override val destination: RouteDestination = RouteDestination.Home.Root()
    }

    data class SaveItem(val createRaw: DetailPaneInformation.CreateRaw) :
        Request<SaveItemDestination> {
        override val destination: SaveItemDestination
            get() = SaveItemDestination(createRaw)
    }

    data object None : Request<Nothing> {
        override val destination: Nothing
            get() = throw NotImplementedError("This should never be called")
    }
}