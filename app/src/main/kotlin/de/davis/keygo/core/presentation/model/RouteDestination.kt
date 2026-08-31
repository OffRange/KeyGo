package de.davis.keygo.core.presentation.model

import androidx.navigation3.runtime.NavKey
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import kotlinx.serialization.Serializable
import java.util.UUID

sealed interface RouteDestination : NavKey {

    @Serializable
    data object Home : RouteDestination

    /** A destination that fills the dashboard's detail pane, or the window once there is one. */
    sealed interface Detail : RouteDestination

    @Serializable
    data class ViewItem(val itemId: String) : Detail {

        constructor(itemId: ItemId) : this(itemId.toString())

        val id: ItemId get() = UUID.fromString(itemId)
    }

    sealed interface Form : Detail {
        val itemType: VaultItemType
    }

    @Serializable
    data class CreateItem(override val itemType: VaultItemType) : Form

    @Serializable
    data class EditItem(
        override val itemType: VaultItemType,
        val itemId: String,
    ) : Form {

        constructor(itemType: VaultItemType, itemId: ItemId) : this(itemType, itemId.toString())

        val id: ItemId get() = UUID.fromString(itemId)
    }

    @Serializable
    data object SelectItemType : RouteDestination

    @Serializable
    data object Connectivity : RouteDestination

    @Serializable
    data object Libraries : RouteDestination
}
